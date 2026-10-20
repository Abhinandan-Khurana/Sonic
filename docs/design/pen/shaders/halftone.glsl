precision mediump float;

/** @resolution */
uniform vec2 u_resolution;

/**
 * @label Cell size
 * @default 7
 * @range 2, 40
 */
uniform float u_cell;

/**
 * @label Ink
 * @color
 * @default #FF2E88
 */
uniform vec3 u_ink;

/**
 * @label Opacity
 * @default 0.35
 * @range 0, 1
 */
uniform float u_opacity;

/**
 * @label Angle
 * @default 0.785
 * @range 0, 3.14
 */
uniform float u_angle;

/**
 * @label Falloff
 * @default 1.2
 * @range 0.2, 4
 */
uniform float u_falloff;

void main() {
  vec2 p = gl_FragCoord.xy;
  float c = cos(u_angle);
  float s = sin(u_angle);
  vec2 r = mat2(c, -s, s, c) * p;
  vec2 cell = fract(r / u_cell) - 0.5;
  vec2 uv = gl_FragCoord.xy / u_resolution;
  float tone = mix(0.95, 0.05, pow(clamp((uv.x + (1.0 - uv.y)) * 0.5, 0.0, 1.0), u_falloff));
  float d = length(cell);
  float rad = 0.5 * tone;
  float a = 1.0 - smoothstep(rad - 0.06, rad + 0.06, d);
  float alpha = a * u_opacity;
  gl_FragColor = vec4(u_ink * alpha, alpha);
}
