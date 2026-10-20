precision mediump float;

/** @resolution */
uniform vec2 u_resolution;

/**
 * @label Amount
 * @default 0.10
 * @range 0, 0.5
 */
uniform float u_amount;

/**
 * @label Grain size
 * @default 1.5
 * @range 0.5, 4
 */
uniform float u_scale;

/**
 * @label Speck colour
 * @color
 * @default #0B0A10
 */
uniform vec3 u_speck;

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
  vec2 p = floor(gl_FragCoord.xy / u_scale);
  float n = hash(p);
  float speck = step(0.992, hash(p + 17.0));
  vec3 col = mix(n < 0.5 ? vec3(0.0) : vec3(1.0), u_speck, speck);
  float alpha = clamp(u_amount * abs(n - 0.5) * 2.0 + speck * 0.55, 0.0, 1.0);
  gl_FragColor = vec4(col * alpha, alpha);
}
