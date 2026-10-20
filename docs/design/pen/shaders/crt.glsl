precision mediump float;

/** @resolution */
uniform vec2 u_resolution;

/** @time */
uniform float u_time;

/**
 * @label Line spacing
 * @default 3
 * @range 2, 8
 */
uniform float u_spacing;

/**
 * @label Scanline strength
 * @default 0.45
 * @range 0, 1
 */
uniform float u_strength;

/**
 * @label Glow
 * @color
 * @default #C6FF00
 */
uniform vec3 u_glow;

void main() {
  vec2 uv = gl_FragCoord.xy / u_resolution;
  float line = step(0.5, fract(gl_FragCoord.y / u_spacing));
  float band = 1.0 - smoothstep(0.0, 0.12, abs(fract(uv.y - u_time * 0.12) - 0.5));
  float vig = smoothstep(0.35, 0.95, length((uv - 0.5) * vec2(1.0, 1.6)));
  float a = u_strength * (1.0 - line) + vig * 0.45 + band * 0.10;
  vec3 col = mix(vec3(0.0), u_glow, band * 0.6);
  float alpha = clamp(a, 0.0, 1.0);
  gl_FragColor = vec4(col * alpha, alpha);
}
