import { Canvas, useFrame } from "@react-three/fiber";
import { Component, useMemo, useRef, useState, type ReactNode } from "react";
import * as THREE from "three";

const vertexShader = /* glsl */ `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

// Square-grid shader: draws thin lines along UV, no diagonals.
const fragmentShader = /* glsl */ `
  precision mediump float;
  varying vec2 vUv;
  uniform vec2 uCells;
  uniform float uLineWidth;
  uniform vec3 uColor;
  uniform float uOpacity;

  void main() {
    vec2 grid = fract(vUv * uCells);
    vec2 d = min(grid, 1.0 - grid);
    float line = min(d.x, d.y);
    float aa = fwidth(line);
    float alpha = 1.0 - smoothstep(uLineWidth, uLineWidth + aa, line);
    if (alpha <= 0.001) discard;
    gl_FragColor = vec4(uColor, alpha * uOpacity);
  }
`;

function WaveMesh() {
  const meshRef = useRef<THREE.Mesh>(null);

  const uniforms = useMemo(
    () => ({
      uCells: { value: new THREE.Vector2(80, 100) },
      uLineWidth: { value: 0.02 },
      uColor: { value: new THREE.Color("#ffffff") },
      uOpacity: { value: 0.35 },
    }),
    []
  );

  useFrame(({ clock }) => {
    const mesh = meshRef.current;
    if (!mesh) return;
    const geom = mesh.geometry as THREE.PlaneGeometry;
    const pos = geom.attributes.position as THREE.BufferAttribute;
    const t = clock.getElapsedTime();
    for (let i = 0; i < pos.count; i++) {
      const x = pos.getX(i);
      const y = pos.getY(i);
      const z =
        Math.sin(x * 1.2 + t) * 0.15 +
        Math.cos(y * 1.5 + t * 0.8) * 0.15 +
        Math.sin((x + y) * 0.5 - t) * 0.2 +
        Math.cos(x * 0.4 - t * 0.6) * 0.1;
      pos.setZ(i, z);
    }
    pos.needsUpdate = true;
  });

  return (
    <mesh ref={meshRef} rotation={[-Math.PI / 2, 0, 0]} position={[0, -2.2, 0]}>
      <planeGeometry args={[30, 40, 80, 100]} />
      <shaderMaterial
        vertexShader={vertexShader}
        fragmentShader={fragmentShader}
        uniforms={uniforms}
        transparent
        depthWrite={false}
        side={THREE.DoubleSide}
      />
    </mesh>
  );
}

/**
 * 这一层只是 §4 的背景装饰。three.js 拿不到 WebGL 上下文时会直接抛错，
 * 而 §4 是路由组件的一层 —— 不拦的话异常一路冒到 TanStack Router 的根错误边界，
 * 整页会被换成 "Something went wrong!"，正文一个字都看不到。
 *
 * 实测环境：无头浏览器（--disable-gpu）必现；关闭硬件加速的 Chrome、
 * 部分远程桌面/虚拟机同样会命中。
 *
 * 两道防线：
 *   1. 进不进得来 —— 先做能力探测，没有 WebGL 就不去创建上下文，顺带不刷那条吓人的控制台报错；
 *   2. 进去后炸了 —— 错误边界兜住（上下文丢失、着色器编译失败等探测查不出来的情况）。
 * 两者都只影响这一层装饰，页面其余部分照常渲染。
 */
function webglAvailable(): boolean {
  if (typeof document === "undefined") return false;
  try {
    const c = document.createElement("canvas");
    return !!(c.getContext("webgl2") || c.getContext("webgl"));
  } catch {
    return false;
  }
}

class WaveGridBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(err: unknown) {
    console.warn("[WaveGrid] WebGL 不可用，已跳过 §4 的背景装饰层（不影响页面其余部分）：", err);
  }

  render() {
    return this.state.failed ? null : this.props.children;
  }
}

export function WaveGrid() {
  // 探测只在挂载时做一次（惰性初始化），结果不随任何交互变化。
  const [ok] = useState(webglAvailable);
  if (!ok) return null;

  return (
    <WaveGridBoundary>
      <Canvas camera={{ position: [0, 1.33, 5], fov: 45 }} dpr={[1, 2]}>
        <WaveMesh />
      </Canvas>
    </WaveGridBoundary>
  );
}
