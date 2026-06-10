#version 150

// Full-screen splash background effect.
// texCoord is 0..1 across the whole screen (UV set by the quad).
in vec2 texCoord;
in vec4 vertexColor;

uniform vec4 ColorModulator;

// Animation driver (seconds since splash start).
uniform float STime;
// Per-stage progress values in 0..1 (computed CPU-side from the timeline).
uniform float ScanProgress;     // stage 1: border glow + scanline sweep
uniform float TearProgress;     // stage 3: CRT screen tear amount (0 = none)
uniform float DissolveProgress; // stage 5: edge->center dust dissolve (0 = none, 1 = gone)
uniform vec2  ScreenSize;       // pixels

out vec4 fragColor;

const vec3 ACCENT = vec3(0.22, 0.78, 1.0);   // cyan energy accent
const vec3 ACCENT2 = vec3(0.55, 0.35, 1.0);  // violet secondary

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

void main() {
    vec2 uv = texCoord;
    vec3 col = vec3(0.0);

    // --- Dark base with a faint radial vignette glow ---
    vec2 centered = uv - 0.5;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    centered.x *= aspect;
    float dist = length(centered);
    float baseGlow = smoothstep(0.9, 0.0, dist) * 0.06;
    col += vec3(0.02, 0.03, 0.05) + ACCENT * baseGlow;

    // --- Animated glowing border frame (stage 1, persists after) ---
    float borderReveal = ScanProgress;
    // distance to nearest screen edge in uv space
    float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float borderBand = smoothstep(0.018, 0.0, edge);          // thin band near edge
    float borderInner = smoothstep(0.045, 0.018, edge) * 0.4; // soft inner falloff
    float pulse = 0.6 + 0.4 * sin(STime * 4.0);
    vec3 borderCol = mix(ACCENT, ACCENT2, 0.5 + 0.5 * sin(STime * 1.5 + uv.x * 6.0));
    col += borderCol * (borderBand + borderInner) * borderReveal * pulse * 1.3;

    // --- Scanline sweep (stage 1): a bright horizontal line that travels down then up ---
    if (ScanProgress > 0.0 && ScanProgress < 1.0) {
        // two passes: 0->0.5 sweeps down, 0.5->1 sweeps up
        float sweepY;
        if (ScanProgress < 0.5) sweepY = ScanProgress * 2.0;
        else sweepY = 1.0 - (ScanProgress - 0.5) * 2.0;
        float line = smoothstep(0.035, 0.0, abs(uv.y - sweepY));
        col += ACCENT * line * 0.9;
        // faint trailing grid scan tint
        col += ACCENT * line * 0.3 * step(0.5, fract(uv.x * 80.0));
    }

    // --- Persistent subtle scanlines (CRT feel) ---
    float scan = 0.92 + 0.08 * sin(uv.y * ScreenSize.y * 1.4);
    col *= scan;

    // --- CRT screen tear (stage 3) ---
    // handled mostly on the logo; here we add horizontal RGB-split banding + jitter glow
    if (TearProgress > 0.0) {
        float band = floor(uv.y * 18.0);
        float jitter = (hash21(vec2(band, floor(STime * 24.0))) - 0.5);
        float tearMask = step(0.55, hash21(vec2(band, floor(STime * 12.0))));
        col += ACCENT * abs(jitter) * tearMask * TearProgress * 0.5;
        // horizontal black retrace lines
        float retrace = smoothstep(0.0, 0.5, abs(jitter)) * tearMask;
        col *= mix(1.0, 0.6, retrace * TearProgress);
    }

    // --- Alpha & edge->center dust dissolve (stage 5) ---
    float alpha = 1.0;

    if (DissolveProgress > 0.0) {
        // Dissolve front travels from edges (dist large) toward center (dist small).
        // Normalize dist to 0..1 (corner ~ 0.5*sqrt(1+aspect^2)); use a stable scale.
        float maxd = 0.5 * sqrt(1.0 + aspect * aspect);
        float nd = dist / maxd;                  // 0 center .. 1 corner
        // front position: starts at 1 (corner), moves to -0.2 (past center)
        float front = 1.0 - DissolveProgress * 1.2;
        float grain = noise(uv * 40.0) * 0.12;
        // pixels outside the front (nd > front) become dust
        float dustEdge = smoothstep(front, front + 0.12 + grain, nd);
        // dust shimmer at the dissolving front
        float frontGlow = smoothstep(0.18, 0.0, abs(nd - front));
        col += ACCENT * frontGlow * 1.2;
        // sparkle particles along the front
        float spark = step(0.92, hash21(floor(uv * ScreenSize / 6.0) + floor(STime * 30.0)));
        col += ACCENT2 * spark * frontGlow * 2.0;
        alpha *= (1.0 - dustEdge);
    }

    fragColor = vec4(col, alpha) * ColorModulator;
}
