#version 150

in vec2 texCoord;
in vec4 vertexColor;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

uniform float STime;
uniform float FadeIn;
uniform float Mosaic;
uniform float TearProgress;
uniform float LightScan;
uniform float DissolveProgress;
uniform vec2  LogoSize;

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

void finishLogo(vec3 col, float alpha, float texA, vec2 uv) {
    if (LightScan >= 0.0 && LightScan <= 1.0) {
        float scanY = LightScan;
        float d = uv.y - scanY;
        float lineGlow = smoothstep(0.06, 0.0, abs(d));
        float trail = smoothstep(0.22, 0.0, max(0.0, scanY - uv.y));
        float g = lineGlow + trail * 0.45;
        col += ACCENT * g * texA * 1.6;
        col = mix(col, col + ACCENT * 0.5, trail * texA);
    }

    alpha *= clamp(FadeIn, 0.0, 1.0);

    if (DissolveProgress > 0.0) {
        vec2 c = uv - 0.5;
        c.x *= LogoSize.x / max(LogoSize.y, 1.0);
        float nd = length(c) / (0.5 * LogoSize.x / max(LogoSize.y, 1.0));
        nd = clamp(nd, 0.0, 1.0);
        float front = 1.0 - DissolveProgress * 1.2;
        float grain = noise(uv * 60.0) * 0.14;
        float dust = smoothstep(front, front + 0.12 + grain, nd);
        float frontGlow = smoothstep(0.16, 0.0, abs(nd - front));
        col += ACCENT * frontGlow * 1.4 * texA;
        float spark = step(0.9, hash21(floor(uv * LogoSize / 5.0) + floor(STime * 28.0)));
        col += ACCENT * spark * frontGlow * 2.2 * texA;
        alpha *= (1.0 - dust);
    }

    fragColor = vec4(col, alpha) * vertexColor * ColorModulator;
}

void main() {
    vec2 uv = texCoord;

    float rgbSplit = 0.0;
    if (TearProgress > 0.0) {
        float band = floor(uv.y * 22.0);
        float roll = hash21(vec2(band, floor(STime * 18.0)));
        float shift = (roll - 0.5) * 0.08 * TearProgress * step(0.5, roll);
        uv.x += shift;
        rgbSplit = 0.006 * TearProgress * step(0.6, roll);
    }

    float mosaicAmt = clamp(Mosaic, 0.0, 1.0);

    if (mosaicAmt > 0.001) {
        float minCellsY = 14.0;
        float maxCellsY = LogoSize.y;
        float k = mosaicAmt * mosaicAmt;
        float cellsY = mix(maxCellsY, minCellsY, k);
        float aspect = LogoSize.x / max(LogoSize.y, 1.0);
        vec2 grid = vec2(cellsY * aspect, cellsY);
        vec2 cellOrigin = floor(uv * grid) / grid;
        vec2 cellSize = 1.0 / grid;

        vec4 acc = vec4(0.0);
        for (int yy = 0; yy < 4; yy++) {
            for (int xx = 0; xx < 4; xx++) {
                vec2 o = (vec2(float(xx), float(yy)) + 0.5) / 4.0;
                acc += texture(Sampler0, cellOrigin + o * cellSize);
            }
        }
        vec4 blockTex = acc / 16.0;
        blockTex.a = clamp(blockTex.a * 2.2, 0.0, 1.0);

        vec4 sharpTex = texture(Sampler0, uv);
        vec4 tex = mix(sharpTex, blockTex, mosaicAmt);

        if (rgbSplit > 0.0) {
            float r = texture(Sampler0, uv + vec2(rgbSplit, 0.0)).r;
            float b = texture(Sampler0, uv - vec2(rgbSplit, 0.0)).b;
            tex.r = mix(tex.r, r, 0.5);
            tex.b = mix(tex.b, b, 0.5);
        }

        finishLogo(tex.rgb, tex.a, tex.a, uv);
        return;
    }

    vec4 tex;
    if (rgbSplit > 0.0) {
        float r = texture(Sampler0, uv + vec2(rgbSplit, 0.0)).r;
        vec4 g = texture(Sampler0, uv);
        float b = texture(Sampler0, uv - vec2(rgbSplit, 0.0)).b;
        tex = vec4(r, g.g, b, g.a);
    } else {
        tex = texture(Sampler0, uv);
    }

    finishLogo(tex.rgb, tex.a, tex.a, uv);
}
