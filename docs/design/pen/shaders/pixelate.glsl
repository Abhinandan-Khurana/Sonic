precision mediump float;

/** @resolution */
uniform vec2 u_resolution;

/**
 * @label Image
 */
uniform sampler2D u_image;

/**
 * @label Pixel size
 * @default 6
 * @range 1, 32
 */
uniform float u_pixel;

/**
 * @label Colour levels
 * @default 5
 * @range 2, 16
 */
uniform float u_levels;

/**
 * @label Dither
 * @default 0.6
 * @range 0, 1
 */
uniform float u_dither;

/**
 * @label Darken
 * @default 0
 * @range 0, 1
 */
uniform float u_darken;

/**
 * @label Flip vertical
 * @default 1
 * @range 0, 1
 */
uniform float u_flip;

float bayer2(vec2 a) {
  a = floor(a);
  return fract(dot(a, vec2(0.5, a.y * 0.75)));
}

float bayer4(vec2 a) {
  return bayer2(0.5 * a) * 0.25 + bayer2(a);
}

void main() {
  vec2 cell = floor(gl_FragCoord.xy / u_pixel);
  vec2 uv = (cell + 0.5) * u_pixel / u_resolution;
  uv.y = mix(uv.y, 1.0 - uv.y, step(0.5, u_flip));
  vec3 c = texture2D(u_image, clamp(uv, 0.0, 1.0)).rgb;
  float b = bayer4(cell) - 0.5;
  float steps = u_levels - 1.0;
  c = floor(c * steps + b * u_dither + 0.5) / steps;
  c *= 1.0 - u_darken;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
