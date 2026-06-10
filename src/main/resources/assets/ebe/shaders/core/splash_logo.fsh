#version 150

// Logo effect shader. Samples the logo texture (Sampler0) and applies the
// staged splash effects. texCoord is the logo's own 0..1 UV.
in vec2 texCoord;
in vec4 vertexColor;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

uniform float STime;
uniform float FadeIn;          // stage 2: 0..1 overall logo opacity ramp
uniform float Mosaic;          // stage 2: mosaic strength, 1 = very chunky, 0 = sharp
uniform float TearProgress;    // stage 3: CRT tear amount (0 = none)
uniform float LightScan;       // stage 4: 0..1 top->bottom light scan position (>1 = inactive)
uniform float DissolveProgress;// stage 5: 0..1 dust dissolve (logo also dissolves edge->center)
uniform vec2  LogoSize;        // logo texture pixel size

out vec4 fragColor;

const vec3 ACCENT = vec3(0.30, 0.85, 1.0);

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

    // --- CRT tear: horizontal displacement per band + RGB split (stage 3) ---
    float rgbSplit = 0.0;
    if (TearProgress > 0.0) {
        float band = floor(uv.y * 22.0);
        float roll = hash21(vec2(band, floor(STime * 18.0)));
        float shift = (roll - 0.5) * 0.08 * TearProgress * step(0.5, roll);
        uv.x += shift;
        rgbSplit = 0.006 * TearProgress * step(0.6, roll);
    }

    // --- Mosaic / pixelation that resolves from chunky -> sharp (stage 2) ---
    vec2 sampleUv = uv;
    if (Mosaic > 0.001) {
        // cells across the logo: few when Mosaic high, many (=sharp) when low
        float minCells = 6.0;
        float maxCells = max(LogoSize.x, LogoSize.y);
        float cells = mix(maxCells, minCells, clamp(Mosaic, 0.0, 1.0));
        vec2 grid = vec2(cells * (LogoSize.x / max(LogoSize.y, 1.0)), cells);
        sampleUv = (floor(uv * grid) + 0.5) / grid;
    }

    vec4 tex;
    if (rgbSplit > 0.0) {
        float r = texture(Sampler0, sampleUv + vec2(rgbSplit, 0.0)).r;
        vec4 g = texture(Sampler0, sampleUv);
        float b = texture(Sampler0, sampleUv - vec2(rgbSplit, 0.0)).b;
        tex = vec4(r, g.g, b, g.a);
    } else {
        tex = texture(Sampler0, sampleUv);
    }

    vec3 col = tex.rgb;
    float alpha = tex.a;

    // --- Top-to-bottom light scan glow over the text (stage 4) ---
    if (LightScan >= 0.0 && LightScan <= 1.0) {
        float scanY = LightScan;
        float d = uv.y - scanY;
        // bright leading line + soft glow trailing above
        float lineGlow = smoothstep(0.06, 0.0, abs(d));
        float trail = smoothstep(0.22, 0.0, max(0.0, scanY - uv.y));
        float g = lineGlow + trail * 0.45;
        col += ACCENT * g * tex.a * 1.6;
        // brighten the logo itself where the scan passed (energized look)
        col = mix(col, col + ACCENT * 0.5, trail * tex.a);
    }

    // --- Fade in (stage 2) ---
    alpha *= clamp(FadeIn, 0.0, 1.0);

    // --- Dust dissolve: logo dissolves edge->center with the screen (stage 5) ---
    if (DissolveProgress > 0.0) {
        vec2 c = uv - 0.5;
        c.x *= LogoSize.x / max(LogoSize.y, 1.0);
        float nd = length(c) / (0.5 * LogoSize.x / max(LogoSize.y, 1.0)); // ~0 center .. ~1 edge
        nd = clamp(nd, 0.0, 1.0);
        float front = 1.0 - DissolveProgress * 1.2;
        float grain = noise(uv * 60.0) * 0.14;
        float dust = smoothstep(front, front + 0.12 + grain, nd);
        float frontGlow = smoothstep(0.16, 0.0, abs(nd - front));
        col += ACCENT * frontGlow * 1.4 * tex.a;
        // particle sparkle at the front
        float spark = step(0.9, hash21(floor(uv * LogoSize / 5.0) + floor(STime * 28.0)));
        col += ACCENT * spark * frontGlow * 2.2 * tex.a;
        alpha *= (1.0 - dust);
    }

    fragColor = vec4(col, alpha) * vertexColor * ColorModulator;
}
