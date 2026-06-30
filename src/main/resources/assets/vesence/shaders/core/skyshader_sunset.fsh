#version 150

uniform vec2 resolution;
uniform float time;
uniform float alpha;
uniform float yaw;
uniform float pitch;
uniform float focal;
uniform vec3 PrimaryColor;
uniform vec3 SecondaryColor;
uniform vec3 SunDir;
uniform float Haze;

out vec4 fragColor;

float hash2(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 21.21);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 x) {
    vec2 i = floor(x);
    vec2 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm2(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise2(p);
        p *= 2.0;
        p += vec2(3.7, 1.3);
        a *= 0.5;
    }
    return v;
}

vec3 renderSunset(vec3 direction, float t) {
    vec3 dir = normalize(direction);
    float elevation = dir.y;
    float azimuth = atan(dir.z, dir.x);

    // Sunset gradient: orange/red horizon -> purple/indigo zenith.
    vec3 horizonColor = vec3(1.0, 0.45, 0.18);   // warm orange
    vec3 midColor = vec3(0.85, 0.25, 0.30);      // red-pink
    vec3 zenithColor = vec3(0.12, 0.08, 0.28);   // deep indigo
    vec3 sky = mix(horizonColor, midColor, smoothstep(-0.1, 0.25, elevation));
    sky = mix(sky, zenithColor, smoothstep(0.2, 0.85, elevation));

    // Blend the theme colour in: warm it up so it suits sunset.
    vec3 warmTheme = mix(PrimaryColor, vec3(1.0, 0.6, 0.3), 0.45);
    sky = mix(sky, mix(sky, warmTheme, 0.55), smoothstep(-0.2, 0.5, elevation));

    vec3 sunDir = normalize(SunDir);
    float sunDot = clamp(dot(dir, sunDir), -1.0, 1.0);
    float sunAngle = acos(sunDot);

    float sunDisc = smoothstep(0.998, 0.9997, sunDot);
    float sunGlow = exp(-sunAngle * 8.0) * 0.7;
    float sunHalo = exp(-sunAngle * 3.5) * 0.45;
    float corona = exp(-abs(sunAngle - 0.15) * 20.0) * 0.30;

    float rayAngle = azimuth * 7.0;
    float rays = pow(max(sunDot, 0.0), 18.0) * (0.4 + 0.6 * pow(abs(sin(rayAngle + t * 0.05)), 2.0));
    rays *= smoothstep(-0.05, 0.4, elevation);

    // Sunset clouds: warm-lit bottoms, golden tops.
    float cloudNoise = 0.0;
    if (elevation > -0.1) {
        vec2 cloudPos = dir.xz / max(elevation + 0.3, 0.1) * 4.0 + vec2(t * 0.02, t * 0.01);
        float c1 = fbm2(cloudPos);
        float c2 = fbm2(cloudPos * 2.3 + vec2(t * 0.015, -t * 0.01));
        cloudNoise = smoothstep(0.45, 0.78, c1 * 0.65 + c2 * 0.35);

        float cloudLit = smoothstep(-0.05, 0.3, elevation) * cloudNoise;
        sky = mix(sky, vec3(0.95, 0.45, 0.18), cloudLit * 0.6);

        float cloudTop = smoothstep(0.2, 0.7, elevation) * cloudNoise;
        sky = mix(sky, vec3(1.0, 0.80, 0.45), cloudTop * 0.35);
    }

    // Horizon band: shifts hue around the sky.
    float band = smoothstep(-0.15, 0.0, elevation) * smoothstep(0.12, 0.0, elevation);
    vec3 bandColor = mix(vec3(0.85, 0.32, 0.16), vec3(0.45, 0.16, 0.38), sin(azimuth * 2.0 + t * 0.03) * 0.5 + 0.5);
    sky += bandColor * band * 0.45;

    vec3 color = sky;
    // Sun composition - bright white-yellow disc with warm halo.
    color += vec3(1.0, 0.95, 0.8) * sunDisc * 4.0;
    color += vec3(1.0, 0.7, 0.35) * sunGlow;
    color += vec3(1.0, 0.7, 0.3) * sunHalo;
    color += warmTheme * corona;
    color += vec3(1.0, 0.6, 0.2) * rays * 0.35;

    float haze = smoothstep(0.3, -0.2, elevation) * Haze;
    color = mix(color, vec3(0.95, 0.55, 0.30), haze * 0.4);

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

    vec3 color = renderSunset(rd, t);

    color += hash2(gl_FragCoord.xy) * 0.005;

    fragColor = vec4(color, alpha);
}
