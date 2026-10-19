use super::*;
use std::{net::{TcpListener,TcpStream},io::{Read,Write},thread};

fn fixture() -> Vec<u8> {
    let mut bytes=vec![0;42];bytes[..4].copy_from_slice(b"fLaC");bytes[4]=0x80;bytes[7]=34;
    bytes[8..12].copy_from_slice(&[0,16,0,16]);
    let packed=(44_100u64<<44)|(1u64<<41)|(15u64<<36)|44_100;
    bytes[18..26].copy_from_slice(&packed.to_be_bytes());bytes
}
fn string(value:&str)->Vec<u8>{let mut b=(value.len() as u32).to_le_bytes().to_vec();b.extend(value.as_bytes());b}
fn frame(stream:&mut TcpStream,code:u32,payload:&[u8]) {
    stream.write_all(&((4+payload.len())as u32).to_le_bytes()).unwrap();stream.write_all(&code.to_le_bytes()).unwrap();stream.write_all(payload).unwrap();
}
fn read_frame(stream:&mut TcpStream)->io::Result<(u32,Vec<u8>)>{
    let mut size=[0;4];stream.read_exact(&mut size)?;let size=u32::from_le_bytes(size)as usize;
    if !(4..=65536).contains(&size){return Err(io::ErrorKind::InvalidData.into());}
    let mut b=vec![0;size];stream.read_exact(&mut b)?;
    Ok((u32::from_le_bytes(b[..4].try_into().unwrap()),b[4..].to_vec()))
}
fn wait_event(runtime:&Runtime,kind:&str)->Value {
    let started=Instant::now();
    while started.elapsed()<Duration::from_secs(8){
        for event in runtime.poll().as_array().unwrap(){if event["type"]==kind{return event.clone();}}
        thread::sleep(Duration::from_millis(20));
    }
    panic!("No {kind} event")
}

#[test]
fn flac_content_and_extension_are_independent_guards(){
    assert!(flac_path("Artist\\Album\\TRACK.FLAC"));
    assert!(!flac_path("Artist\\Track.mp3"));assert!(!flac_path("..\\track.flac"));
    let path=std::env::temp_dir().join(format!("runout-flac-{}",std::process::id()));
    std::fs::write(&path,fixture()).unwrap();
    assert_eq!(check_flac(&mut File::open(&path).unwrap()).unwrap(),(44100,16,1));
    std::fs::write(&path,b"ID3 fake renamed mp3 content................................").unwrap();
    assert!(check_flac(&mut File::open(&path).unwrap()).is_err());let _=std::fs::remove_file(path);
}

#[test]
fn commands_reject_unauthed_actions_and_invalid_accounts(){
    let r=Runtime::new(Arc::new(|_|Err(io::ErrorKind::PermissionDenied.into())));
    assert!(r.execute(json!({"op":"login","username":" bad ","password":"p"})).is_err());
    assert!(r.execute(json!({"op":"download","id":"x","path":"track.mp3"})).is_err());
    assert!(r.execute(json!({"op":"message","username":"peer","text":"test","clientId":"m"})).is_err());
    assert_eq!(r.poll(),json!([]));
}

#[test]
fn loopback_login_private_message_is_not_acked_until_host_commits(){
    let listener=TcpListener::bind(("127.0.0.1",0)).unwrap();let port=listener.local_addr().unwrap().port();
    let (acked_tx,acked_rx)=std::sync::mpsc::channel();
    let server=thread::spawn(move||{
        let(mut stream,_)=listener.accept().unwrap();stream.set_read_timeout(Some(Duration::from_secs(8))).unwrap();
        let(code,_)=read_frame(&mut stream).unwrap();assert_eq!(code,1);
        let mut login=vec![1];login.extend(string("local test server"));login.extend([127,0,0,1]);login.extend(string(""));login.push(0);frame(&mut stream,1,&login);
        let mut dm=42u32.to_le_bytes().to_vec();dm.extend(123u32.to_le_bytes());dm.extend(string("testpeer"));dm.extend(string("test message"));dm.push(0);frame(&mut stream,22,&dm);
        while let Ok((code,payload))=read_frame(&mut stream){if code==23 {acked_tx.send(u32::from_le_bytes(payload[..4].try_into().unwrap())).unwrap();break;}}
    });
    let r=Runtime::new(Arc::new(|_|Err(io::ErrorKind::PermissionDenied.into())));
    r.execute(json!({"op":"login","username":"local-test","password":"test","host":"127.0.0.1","port":port})).unwrap();
    let dm=wait_event(&r,"message");assert_eq!(dm["text"],"test message");assert_eq!(dm["replayed"],true);
    assert!(acked_rx.recv_timeout(Duration::from_millis(200)).is_err(),"Receiving before persistence must not ACK");
    r.execute(json!({"op":"ack","id":42})).unwrap();assert_eq!(acked_rx.recv_timeout(Duration::from_secs(3)).unwrap(),42);
    r.execute(json!({"op":"logout"})).unwrap();server.join().unwrap();
}
