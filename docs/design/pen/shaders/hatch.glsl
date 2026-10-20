precision mediump float;

/** @resolution */
uniform vec2 u_resolution;

/**
 * @label Ink
 * @color
 * @default #2B2925
 */
uniform vec3 u_ink;

/**
 * @label Line spacing
 * @default 4
 * @range 2, 16
 */
uniform float u_spacing;

/**
 * @label Edge shading
 * @default 0.35
 * @range 0, 1
 */
uniform float u_edge;

/**
 * @label Base shading
 * @default 0.05
 * @range 0, 0.5
 */
uniform float u_base;

void main() {
  vec2 p = gl_FragCoord.xy;
  vec2 uv = p / u_resolution;
  float dist = abs(fract((p.x + p.y) / u_spacing) - 0.5) * u_spacing;
  float line = 1.0 - smoothstep(0.2, 0.9, dist);
  vec2 d = min(uv, 1.0 - uv);
  float edge = 1.0 - smoothstep(0.0, 0.14, min(d.x, d.y));
  float alpha = clamp(line * (u_base + edge * u_edge), 0.0, 1.0);
  gl_FragColor = vec4(u_ink * alpha, alpha);
}
