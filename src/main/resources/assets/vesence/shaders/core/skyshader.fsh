#version 150

uniform vec2 resolution;
uniform float time;
uniform float alpha;
uniform float yaw;
uniform float pitch;
uniform float focal;
uniform vec3 clientColor;

out vec4 fragColor;

float hash(vec3 p) {
    p = fract(p * vec3(.1031, .1030, .0973));
    p += dot(p, p.yzx + 21.21);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec3(1.0, 0.0, 0.0));
    float c = hash(i + vec3(0.0, 1.0, 0.0));
    float d = hash(i + vec3(1.0, 1.0, 0.0));
    float e = hash(i + vec3(0.0, 0.0, 1.0));
    float g = hash(i + vec3(1.0, 0.0, 1.0));
    float h = hash(i + vec3(0.0, 1.0, 1.0));
    float j = hash(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(a, b, f.x), mix(c, d, f.x), f.y),
               mix(mix(e, g, f.x), mix(h, j, f.x), f.y), f.z);
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
    
    float t = time * 0.02;
    
    float n1 = noise(rd * 1.5 + t * 0.3);
    
    vec3 q = vec3(noise(rd + t), noise(rd + 1.25), noise(rd - t));
    float n2 = noise(rd * 3.0 + q * 1.2 + t);
    n2 = mix(n2, noise(rd * 6.0 + q * 0.5 + t), 0.4); 
    
    vec3 col = vec3(0.0);
    vec3 base = clientColor;
    
    vec3 cDeep = mix(base * 0.1, vec3(0.0, 0.0, 0.1), 0.5);
    vec3 cMain = base;
    vec3 cLight = mix(base, vec3(1.0), 0.6);
    
    float density = mix(n1, n2, 0.6);
    col = mix(cDeep, cMain, density);
    col = mix(col, cLight, pow(density, 4.0));
    
    col += cLight * pow(n2, 8.0) * 2.0;
    
    col += hash(vec3(gl_FragCoord.xy, time)) * 0.01;
    
    float finalAlpha = smoothstep(0.1, 0.65, density) * 0.75 * alpha;
    
    fragColor = vec4(col * finalAlpha, finalAlpha);
}
