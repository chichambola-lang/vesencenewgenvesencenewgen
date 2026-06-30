#version 150

uniform vec2 resolution;
uniform float time;
uniform float alpha;
uniform float yaw;
uniform float pitch;
uniform float focal;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 AccentColor;

out vec4 fragColor;

float hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yzx + 21.21);
    return fract((p.x + p.y) * p.z);
}

float hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 21.21);
    return fract((p3.x + p3.y) * p3.z);
}

float noise3(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash3(i);
    float b = hash3(i + vec3(1.0, 0.0, 0.0));
    float c = hash3(i + vec3(0.0, 1.0, 0.0));
    float d = hash3(i + vec3(1.0, 1.0, 0.0));
    float e = hash3(i + vec3(0.0, 0.0, 1.0));
    float g = hash3(i + vec3(1.0, 0.0, 1.0));
    float h = hash3(i + vec3(0.0, 1.0, 0.0));
    float j = hash3(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y),
               mix(mix(e, g, f.x), mix(h, j, f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise3(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

float starLayer(vec3 dir, float density, float threshold) {
    vec3 g = floor(dir * density);
    float h = hash3(g);
    return smoothstep(threshold, 1.0, h) * pow(h, 4.0);
}

vec3 renderStorm(vec3 direction, float t) {
    vec3 dir = normalize(direction);
    float elevation = dir.y;
    float azimuth = atan(dir.z, dir.x);

    // Dark stormy sky: deep blue-grey zenith, lighter grey horizon.
    vec3 zenith = vec3(0.025, 0.030, 0.045);
    vec3 horizon = vec3(0.18, 0.19, 0.22);
    vec3 sky = mix(horizon, zenith, smoothstep(-0.25, 0.75, elevation));

    // Storm clouds - rotating fbm volume, drifting with the wind.
    vec3 cloudPos = dir * 2.4 + vec3(t * 0.05, -t * 0.02, t * 0.04);
    float cloudLarge = fbm3(cloudPos * 0.85);
    float cloudDetail = fbm3(cloudPos * 2.3 + vec3(-t * 0.07, t * 0.03, -t * 0.05));
    float cloudStorm = smoothstep(0.30, 0.74, cloudLarge * 0.6 + cloudDetail * 0.4);

    float turbulence = fbm3(cloudPos * 1.8 + vec3(t * 0.08, -t * 0.05, t * 0.06));
    float darkPatch = smoothstep(0.45, 0.85, turbulence);

    // Cloud body: grey with darker cores. Theme tinted along the lit edges.
    vec3 cloudBase = mix(vec3(0.30, 0.32, 0.38), vec3(0.12, 0.13, 0.17), cloudStorm);
    vec3 cloudCore = mix(vec3(0.06, 0.07, 0.10), vec3(0.02, 0.02, 0.04), cloudStorm);
    cloudBase = mix(cloudBase, cloudCore, darkPatch * 0.85);
    // Theme-colored rim light along cloud edges.
    vec3 cloudColor = mix(cloudBase, cloudBase + PrimaryColor * 0.20, pow(cloudStorm, 3.0));

    float cloudMask = smoothstep(-0.15, 0.45, elevation) * cloudStorm;
    sky = mix(sky, cloudColor, cloudMask);

    // Lightning flashes - two staggered pulses.
    float flashCycle = mod(t * 0.6, 9.0);
    float flash1 = exp(-flashCycle * 5.0) * step(flashCycle, 0.35);
    float flashCycle2 = mod(t * 0.6 + 4.5, 9.0);
    float flash2 = exp(-flashCycle2 * 5.0) * step(flashCycle2, 0.25) * 0.6;

    vec3 flashPos = normalize(vec3(sin(t * 0.2) * 0.4, 0.6, cos(t * 0.15) * 0.4));
    float flashDist = length(dir - flashPos);
    float flashGlow = exp(-flashDist * 5.0) * (flash1 + flash2);

    // Flash tint: warm white core, theme-tinted outer glow.
    vec3 flashColor = vec3(0.95, 0.97, 1.0);
    sky += flashColor * flashGlow * 2.2;
    sky += (AccentColor + vec3(0.4, 0.4, 0.5)) * cloudMask * (flash1 + flash2) * 0.6;

    // Rain streaks near the horizon.
    float rain = 0.0;
    if (elevation < 0.3) {
        float rainAngle = azimuth * 25.0;
        float rainNoise = hash2(vec2(floor(rainAngle), floor(t * 12.0)));
        rain = smoothstep(0.92, 0.98, rainNoise) * smoothstep(0.3, -0.2, elevation) * 0.10;
    }

    // Horizon flash band lit during lightning.
    float horizonFlash = exp(-abs(elevation + 0.05) * 22.0) * (flash1 * 0.4);
    sky += (AccentColor + vec3(0.3, 0.3, 0.4)) * horizonFlash;

    vec3 color = sky;
    color += vec3(rain) * (SecondaryColor * 0.5 + vec3(0.6, 0.65, 0.75));

    // Faint stars high above, only where clouds are absent.
    float starsAbove = starLayer(dir + vec3(t * 0.00005, 0.0, 0.0), 100.0, 0.996) * 0.3;
    color += vec3(starsAbove) * (1.0 - cloudMask) * smoothstep(0.3, 0.8, elevation);

    return color;
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution.xy;
    vec2 p = uv - 0.5;
    p.x *= resolution.x / resolution.y;

    vec3 rdResource = normalize(vec3(p, focal));
    float angY = radians(yaw + 180.0);
    float angP = radians(pitch);
    float cy = cos(angY), sy = sin(angY), cp = cos(angP), sp = sin(angP);

    vec3 rd;
    float y1 = rdResource.y * cp - rdResource.z * sp;
    float z1 = rdResource.y * sp + rdResource.z * cp;
    rd.x = rdResource.x * cy + z1 * sy;
    rd.z = -rdResource.x * sy + z1 * cy;
    rd.y = y1;

    float t = time * 0.5;

    vec3 color = renderStorm(rd, t);

    color += hash3(vec3(gl_FragCoord.xy, time)) * 0.01;

    fragColor = vec4(color, alpha);
}
