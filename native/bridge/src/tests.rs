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
fn stop_removes_tracked_downloads_and_keeps_partial_files() {
    let r = Runtime::new(Arc::new(|_| Err(io::ErrorKind::PermissionDenied.into())));
    let client = Arc::new(Client::with_settings(ClientSettings {
        username: "fixture".into(), password: "fixture".into(),
        server_address: PeerAddress::new("127.0.0.1".into(), 1),
        enable_listen: false, listen_port: 0, accept_children: false,
        shared_directories: Vec::new(), version: ClientVersion { major: 177, minor: 1 },
    }));
    let dir = std::env::temp_dir().join(format!("runout-stop-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    let partial = dir.join("track.flac.part");
    std::fs::write(&partial, b"kept partial bytes").unwrap();
    let (download, receiver) = client.download("track.flac".into(), "peer".into(), 1000,
        dir.to_string_lossy().into_owned()).unwrap();
    lock(&r.transfers).insert("tracked".into(), Transfer {
        username: "peer".into(), path: "track.flac".into(), total: 1000,
        directory: dir.clone(), receiver, state: DownloadStatus::Queued,
        last: String::new(), verified: false, token: download.token,
    });
    *lock(&r.client) = Some(client.clone());
    assert_eq!(client.get_all_downloads().len(), 1);
    r.stop();
    assert!(client.get_all_downloads().is_empty(), "core download token must be removed so a worker cannot keep writing or sleeping");
    assert_eq!(std::fs::read(&partial).unwrap(), b"kept partial bytes");
    assert!(lock(&r.transfers).is_empty());
    let _ = std::fs::remove_dir_all(dir);
}

#[test]
fn a_finished_transfer_is_reported_once_then_no_longer_polled() {
    let r = Runtime::new(Arc::new(|_| Err(io::ErrorKind::PermissionDenied.into())));
    let client = Arc::new(Client::with_settings(ClientSettings {
        username: "fixture".into(), password: "fixture".into(),
        server_address: PeerAddress::new("127.0.0.1".into(), 1),
        enable_listen: false, listen_port: 0, accept_children: false,
        shared_directories: Vec::new(), version: ClientVersion { major: 177, minor: 1 },
    }));
    let dir = std::env::temp_dir().join(format!("runout-finished-{}", std::process::id()));
    std::fs::create_dir_all(&dir).unwrap();
    // The client's own entry for the file (whatever this offline client makes of it), and a
    // status channel the test drives, so the states arrive exactly as written here.
    let _ = client.download("track.flac".into(), "peer".into(), 1000, dir.to_string_lossy().into_owned()).unwrap();
    let (status, receiver) = std::sync::mpsc::channel();
    lock(&r.transfers).insert("done".into(), Transfer {
        username: "peer".into(), path: "track.flac".into(), total: 1000,
        directory: dir.clone(), receiver, state: DownloadStatus::Queued,
        last: String::new(), verified: false, token: u32::MAX,
    });
    *lock(&r.client) = Some(client.clone());
    let first = r.poll();
    assert_eq!(first.as_array().unwrap().iter().filter(|e| e["type"] == "transfer").count(), 1, "queued is reported");
    status.send(DownloadStatus::InProgress { bytes_downloaded: 10, total_bytes: 1000, speed_bytes_per_sec: 1.0 }).unwrap();
    assert_eq!(r.poll()[0]["state"], "downloading");
    assert_eq!(lock(&r.transfers).len(), 1, "a live transfer stays tracked");
    status.send(DownloadStatus::Cancelled).unwrap();
    let events = r.poll();
    let reported: Vec<_> = events.as_array().unwrap().iter().filter(|e| e["type"] == "transfer").collect();
    assert_eq!(reported.len(), 1);
    assert_eq!(reported[0]["state"], "cancelled", "the final state still reaches the host");
    assert!(lock(&r.transfers).is_empty(), "then it is no longer tracked");
    assert!(client.get_all_downloads().is_empty(), "nor kept in the client's own list");
    assert_eq!(r.poll(), json!([]), "later polls carry nothing for it");
    assert!(r.execute(json!({"op":"pause","id":"done"})).is_ok(), "a late pause is a no-op, as before");
    assert!(r.execute(json!({"op":"pause","id":"never"})).is_err(), "an id never seen is still unknown");
    let _ = std::fs::remove_dir_all(dir);
}

fn peer_init(name: &str, kind: &str, token: u32) -> Vec<u8> {
    let mut payload = vec![1]; payload.extend(string(name)); payload.extend(string(kind)); payload.extend(token.to_le_bytes());
    let mut bytes = (payload.len() as u32).to_le_bytes().to_vec(); bytes.extend(payload); bytes
}

#[test]
fn stop_closes_a_paused_real_peer_worker_without_discarding_partial() {
    let server = TcpListener::bind(("127.0.0.1", 0)).unwrap();
    let mut client = Client::with_settings(ClientSettings {
        username: "stop-subject".into(), password: "fixture".into(),
        server_address: PeerAddress::new("127.0.0.1".into(), server.local_addr().unwrap().port()),
        enable_listen: true, listen_port: 0, accept_children: false,
        shared_directories: Vec::new(), version: ClientVersion { major: 177, minor: 1 },
    });
    client.connect().unwrap();
    let address = ("127.0.0.1", client.listen_port().unwrap());
    let client = Arc::new(client);
    let r = Runtime::new(Arc::new(|_| Err(io::ErrorKind::PermissionDenied.into())));
    *lock(&r.client) = Some(client.clone());
    let dir = std::env::temp_dir().join(format!("runout-stop-live-{}", std::process::id()));
    let _ = std::fs::remove_dir_all(&dir); std::fs::create_dir_all(&dir).unwrap();
    let mut control = TcpStream::connect(address).unwrap();
    control.set_read_timeout(Some(Duration::from_secs(3))).unwrap();
    control.write_all(&peer_init("stop-peer", "P", 0)).unwrap();
    frame(&mut control, 15, &[]);
    assert_eq!(read_frame(&mut control).unwrap().0, 16);
    r.execute(json!({"op":"download","id":"live","username":"stop-peer","path":"track.flac","size":524288,"localDirectory":dir})).unwrap();
    assert_eq!(read_frame(&mut control).unwrap().0, 43);
    let token = 700_001u32;
    let mut offer = 1u32.to_le_bytes().to_vec(); offer.extend(token.to_le_bytes()); offer.extend(string("track.flac")); offer.extend(524288u64.to_le_bytes());
    frame(&mut control, 40, &offer);
    assert_eq!(read_frame(&mut control).unwrap().0, 41);
    let mut peer = TcpStream::connect(address).unwrap();
    peer.set_read_timeout(Some(Duration::from_secs(2))).unwrap();
    peer.write_all(&peer_init("stop-peer", "F", token)).unwrap(); peer.write_all(&token.to_le_bytes()).unwrap();
    let mut offset = [0;8]; peer.read_exact(&mut offset).unwrap(); assert_eq!(u64::from_le_bytes(offset), 0);
    peer.write_all(&vec![7;131072]).unwrap();
    let deadline = Instant::now() + Duration::from_secs(3);
    while !client.get_all_downloads().iter().any(|d| matches!(d.status, DownloadStatus::InProgress{bytes_downloaded,..} if bytes_downloaded >= 131072)) {
        assert!(Instant::now() < deadline, "initial progress"); thread::sleep(Duration::from_millis(10));
    }
    assert!(client.pause_download("stop-peer", "track.flac"));
    // Let any in-flight read finish, then the worker enters its 200ms pause wait.
    peer.write_all(&vec![8;65536]).unwrap();
    thread::sleep(Duration::from_millis(250));
    let partial = dir.join("track.flac.part");
    let before = std::fs::read(&partial).unwrap();
    let start = Instant::now(); r.stop();
    let mut byte = [0];
    let closed = match peer.read(&mut byte) {
        Ok(0) => true,
        Err(e) if matches!(e.kind(), io::ErrorKind::ConnectionReset | io::ErrorKind::ConnectionAborted) => true,
        _ => false,
    };
    assert!(closed, "paused worker closes its socket after its token is removed");
    assert!(start.elapsed() < Duration::from_millis(700), "worker exits within one pause cycle plus scheduling allowance");
    assert_eq!(std::fs::read(&partial).unwrap(), before);
    let _ = std::fs::remove_dir_all(dir);
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
