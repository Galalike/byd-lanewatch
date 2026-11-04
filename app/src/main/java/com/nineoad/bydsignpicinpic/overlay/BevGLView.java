package com.nineoad.bydsignpicinpic.overlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import com.nineoad.bydsignpicinpic.R;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * High-performance OpenGL ES 2.0 View for Bird's-Eye View (BEV) 360° Surround View.
 * Renders a top-down ground perspective stitched from the BYD AVM 4-split stream:
 *   - Panel 1 (0.00 - 0.25): REAR (หลัง)
 *   - Panel 2 (0.25 - 0.50): LEFT (ซ้าย)
 *   - Panel 3 (0.50 - 0.75): RIGHT (ขวา)
 *   - Panel 4 (0.75 - 1.00): FRONT (หน้า)
 * Supports full manual calibration: FOV angle control (prevents over-stretching),
 * depth compression, side mirror rotation angles (0°, 90°, 180°, 270°), mirror offset,
 * calibration grid overlay lines (1m, 2m, 3m & 45° seams), and BYD vehicle presets.
 */
public class BevGLView extends GLSurfaceView {
    private static final String TAG = "BevGLView";

    private static final String VERT =
        "attribute vec4 aPos;\n" +
        "attribute vec2 aUv;\n" +
        "varying vec2 vUv;\n" +
        "void main() {\n" +
        "    gl_Position = aPos;\n" +
        "    vUv = aUv;\n" +
        "}\n";

    private static final String FRAG =
        "#extension GL_OES_EGL_image_external : require\n" +
        "precision mediump float;\n" +
        "varying vec2 vUv;\n" +
        "uniform samplerExternalOES uCameraTex;\n" +
        "uniform sampler2D uCarTex;\n" +
        "uniform mat4 uSt;\n" +
        "uniform float uAspect;\n" +
        "uniform vec2 uCarSize;\n" +
        "uniform vec2 uCarOffset;\n" +
        "uniform float uZoom;\n" +
        "uniform float uRadius;\n" +
        "uniform vec2 uWindowSize;\n" +
        "\n" +
        "// Camera angle & perspective parameters\n" +
        "uniform float uFovFront;\n" +
        "uniform float uDepthFront;\n" +
        "uniform float uDistFront;\n" +
        "uniform float uScaleFront;\n" +
        "uniform float uPitchFront;\n" +
        "uniform int uFlipFrontV;\n" +
        "uniform int uMirrorFront;\n" +
        "\n" +
        "uniform float uFovRear;\n" +
        "uniform float uDepthRear;\n" +
        "uniform float uDistRear;\n" +
        "uniform float uScaleRear;\n" +
        "uniform float uPitchRear;\n" +
        "\n" +
        "uniform float uFovSide;\n" +
        "uniform float uDepthSide;\n" +
        "uniform float uDistSide;\n" +
        "uniform float uScaleSide;\n" +
        "uniform float uPitchSide;\n" +
        "uniform float uFishSide;\n" +
        "uniform float uRotSideL;\n" +
        "uniform float uRotSideR;\n" +
        "uniform float uSideCamOffsetY;\n" +
        "uniform int uFlipSideVL;\n" +
        "uniform int uFlipSideVR;\n" +
        "uniform int uMirrorSideL;\n" +
        "uniform int uMirrorSideR;\n" +
        "\n" +
        "uniform float uSeamBlend;\n" +
        "uniform float uSeamAngleFront;\n" +
        "uniform int uFlipRearV;\n" +
        "uniform int uMirrorRear;\n" +
        "uniform int uShowGrid;\n" +
        "\n" +
        "// Per-Camera Roll Tilt (กล้องเอียงซ้าย/ขวาแต่ละตัว)\n" +
        "uniform float uRollFront;\n" +
        "uniform float uRollRear;\n" +
        "uniform float uRollLeft;\n" +
        "uniform float uRollRight;\n" +
        "\n" +
        "// 3D Inverse Perspective Mapping (IPM) + Fisheye Undistortion + 3D Roll/Pitch Transform\n" +
        "vec4 sampleFront(vec2 P, float hw, float hh) {\n" +
        "    float dy = max(0.001, -P.y - hh) / uZoom;\n" +
        "    float dx = P.x / uZoom;\n" +
        "    float H = 0.65;\n" +
        "    float cp = cos(uPitchFront), sp = sin(uPitchFront);\n" +
        "    float Zc = dy * cp + H * sp;\n" +
        "    float Yc = -dy * sp + H * cp;\n" +
        "    float Xc = dx;\n" +
        "    float cr = cos(uRollFront), sr = sin(uRollFront);\n" +
        "    float Xc_rot = cr * Xc - sr * Yc;\n" +
        "    float Yc_rot = sr * Xc + cr * Yc;\n" +
        "    float rho = max(0.0001, length(vec2(Xc_rot, Yc_rot)));\n" +
        "    float theta = atan(rho, Zc);\n" +
        "    float r = theta * (1.0 + uFishSide * theta * theta);\n" +
        "    vec2 sensor = (vec2(Xc_rot, Yc_rot) / rho) * r;\n" +
        "    float spread = sensor.x * uScaleFront;\n" +
        "    float dist = sensor.y * uDepthFront;\n" +
        "    float u = (uMirrorFront == 1) ? (0.875 - spread) : (0.875 + spread);\n" +
        "    float v = (uFlipFrontV == 1) ? (0.50 - dist) : (0.50 + dist);\n" +
        "    u = clamp(u, 0.752, 0.998);\n" +
        "    v = clamp(v, 0.02, 0.98);\n" +
        "    vec2 tc = (uSt * vec4(u, v, 0.0, 1.0)).xy;\n" +
        "    return texture2D(uCameraTex, tc);\n" +
        "}\n" +
        "\n" +
        "vec4 sampleRear(vec2 P, float hw, float hh) {\n" +
        "    float dy = max(0.001, P.y - hh) / uZoom;\n" +
        "    float dx = -P.x / uZoom;\n" +
        "    float H = 0.65;\n" +
        "    float cp = cos(uPitchRear), sp = sin(uPitchRear);\n" +
        "    float Zc = dy * cp + H * sp;\n" +
        "    float Yc = -dy * sp + H * cp;\n" +
        "    float Xc = dx;\n" +
        "    float cr = cos(uRollRear), sr = sin(uRollRear);\n" +
        "    float Xc_rot = cr * Xc - sr * Yc;\n" +
        "    float Yc_rot = sr * Xc + cr * Yc;\n" +
        "    float rho = max(0.0001, length(vec2(Xc_rot, Yc_rot)));\n" +
        "    float theta = atan(rho, Zc);\n" +
        "    float r = theta * (1.0 + uFishSide * theta * theta);\n" +
        "    vec2 sensor = (vec2(Xc_rot, Yc_rot) / rho) * r;\n" +
        "    float spread = sensor.x * uScaleRear;\n" +
        "    float dist = sensor.y * uDepthRear;\n" +
        "    float u = (uMirrorRear == 1) ? (0.125 - spread) : (0.125 + spread);\n" +
        "    float v = (uFlipRearV == 1) ? (0.50 - dist) : (0.50 + dist);\n" +
        "    u = clamp(u, 0.002, 0.248);\n" +
        "    v = clamp(v, 0.02, 0.98);\n" +
        "    vec2 tc = (uSt * vec4(u, v, 0.0, 1.0)).xy;\n" +
        "    return texture2D(uCameraTex, tc);\n" +
        "}\n" +
        "\n" +
        "vec4 sampleLeft(vec2 P, float hw, float hh) {\n" +
        "    float dx = max(0.001, -P.x - hw) / uZoom;\n" +
        "    float dy = (P.y - uSideCamOffsetY) / uZoom;\n" +
        "    float H = 0.85;\n" +
        "    float cp = cos(uPitchSide), sp = sin(uPitchSide);\n" +
        "    float Zc = dx * cp + H * sp;\n" +
        "    float Yc = -dx * sp + H * cp;\n" +
        "    float Xc = dy;\n" +
        "    float cr = cos(uRollLeft), sr = sin(uRollLeft);\n" +
        "    float Xc_rot = cr * Xc - sr * Yc;\n" +
        "    float Yc_rot = sr * Xc + cr * Yc;\n" +
        "    float rho = max(0.0001, length(vec2(Xc_rot, Yc_rot)));\n" +
        "    float theta = atan(rho, Zc);\n" +
        "    float r = theta * (1.0 + uFishSide * theta * theta);\n" +
        "    vec2 sensor = (vec2(Xc_rot, Yc_rot) / rho) * r;\n" +
        "    float dAlong = sensor.x * uScaleSide;\n" +
        "    float dOut = sensor.y * uDepthSide;\n" +
        "    float u = (uMirrorSideL == 1) ? (0.375 - dAlong) : (0.375 + dAlong);\n" +
        "    float v = (uFlipSideVL == 1) ? (0.50 - dOut) : (0.50 + dOut);\n" +
        "    u = clamp(u, 0.252, 0.498);\n" +
        "    v = clamp(v, 0.02, 0.98);\n" +
        "    vec2 tc = (uSt * vec4(u, v, 0.0, 1.0)).xy;\n" +
        "    return texture2D(uCameraTex, tc);\n" +
        "}\n" +
        "\n" +
        "vec4 sampleRight(vec2 P, float hw, float hh) {\n" +
        "    float dx = max(0.001, P.x - hw) / uZoom;\n" +
        "    float dy = -(P.y - uSideCamOffsetY) / uZoom;\n" +
        "    float H = 0.85;\n" +
        "    float cp = cos(uPitchSide), sp = sin(uPitchSide);\n" +
        "    float Zc = dx * cp + H * sp;\n" +
        "    float Yc = -dx * sp + H * cp;\n" +
        "    float Xc = dy;\n" +
        "    float cr = cos(uRollRight), sr = sin(uRollRight);\n" +
        "    float Xc_rot = cr * Xc - sr * Yc;\n" +
        "    float Yc_rot = sr * Xc + cr * Yc;\n" +
        "    float rho = max(0.0001, length(vec2(Xc_rot, Yc_rot)));\n" +
        "    float theta = atan(rho, Zc);\n" +
        "    float r = theta * (1.0 + uFishSide * theta * theta);\n" +
        "    vec2 sensor = (vec2(Xc_rot, Yc_rot) / rho) * r;\n" +
        "    float dAlong = sensor.x * uScaleSide;\n" +
        "    float dOut = sensor.y * uDepthSide;\n" +
        "    float u = (uMirrorSideR == 1) ? (0.625 + dAlong) : (0.625 - dAlong);\n" +
        "    float v = (uFlipSideVR == 1) ? (0.50 - dOut) : (0.50 + dOut);\n" +
        "    u = clamp(u, 0.502, 0.748);\n" +
        "    v = clamp(v, 0.02, 0.98);\n" +
        "    vec2 tc = (uSt * vec4(u, v, 0.0, 1.0)).xy;\n" +
        "    return texture2D(uCameraTex, tc);\n" +
        "}\n" +
        "    v = clamp(v, 0.02, 0.98);\n" +
        "    vec2 tc = (uSt * vec4(u, v, 0.0, 1.0)).xy;\n" +
        "    return texture2D(uCameraTex, tc);\n" +
        "}\n" +
        "\n" +
        "void main() {\n" +
        "    if (uRadius > 0.5) {\n" +
        "        vec2 halfSize = uWindowSize * 0.5;\n" +
        "        float r = min(uRadius, min(halfSize.x, halfSize.y));\n" +
        "        vec2 pCorner = abs(gl_FragCoord.xy - halfSize) - (halfSize - vec2(r));\n" +
        "        float d = length(max(pCorner, 0.0)) - r;\n" +
        "        if (d > 0.0) {\n" +
        "            discard;\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    vec2 P = vec2(vUv.x - 0.5 - uCarOffset.x, (vUv.y - 0.5 - uCarOffset.y) * uAspect);\n" +
        "    float hw = uCarSize.x * 0.5;\n" +
        "    float hh = uCarSize.y * 0.5;\n" +
        "\n" +
        "    vec4 groundCol = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "\n" +
        "    if (P.y < -hh) {\n" +
        "        if (P.x < -hw) {\n" +
        "            vec2 c = vec2(-P.x - hw, -P.y - hh);\n" +
        "            float t = c.y / (max(0.0001, c.x * uSeamAngleFront) + c.y);\n" +
        "            float w = smoothstep(0.48, 0.52, t);\n" +
        "            groundCol = mix(sampleLeft(P, hw, hh), sampleFront(P, hw, hh), w);\n" +
        "            if (abs(c.x * uSeamAngleFront - c.y) < 0.003) {\n" +
        "                groundCol.rgb *= 0.7;\n" +
        "            }\n" +
        "        } else if (P.x > hw) {\n" +
        "            vec2 c = vec2(P.x - hw, -P.y - hh);\n" +
        "            float t = c.y / (max(0.0001, c.x * uSeamAngleFront) + c.y);\n" +
        "            float w = smoothstep(0.48, 0.52, t);\n" +
        "            groundCol = mix(sampleRight(P, hw, hh), sampleFront(P, hw, hh), w);\n" +
        "            if (abs(c.x * uSeamAngleFront - c.y) < 0.003) {\n" +
        "                groundCol.rgb *= 0.7;\n" +
        "            }\n" +
        "        } else {\n" +
        "            groundCol = sampleFront(P, hw, hh);\n" +
        "        }\n" +
        "    } else if (P.y > hh) {\n" +
        "        if (P.x < -hw) {\n" +
        "            vec2 c = vec2(-P.x - hw, P.y - hh);\n" +
        "            float t = c.y / (max(0.0001, c.x) + c.y);\n" +
        "            float w = smoothstep(0.48, 0.52, t);\n" +
        "            groundCol = mix(sampleLeft(P, hw, hh), sampleRear(P, hw, hh), w);\n" +
        "            if (abs(c.x - c.y) < 0.003) {\n" +
        "                groundCol.rgb *= 0.7;\n" +
        "            }\n" +
        "        } else if (P.x > hw) {\n" +
        "            vec2 c = vec2(P.x - hw, P.y - hh);\n" +
        "            float t = c.y / (max(0.0001, c.x) + c.y);\n" +
        "            float w = smoothstep(0.48, 0.52, t);\n" +
        "            groundCol = mix(sampleRight(P, hw, hh), sampleRear(P, hw, hh), w);\n" +
        "            if (abs(c.x - c.y) < 0.003) {\n" +
        "                groundCol.rgb *= 0.7;\n" +
        "            }\n" +
        "        } else {\n" +
        "            groundCol = sampleRear(P, hw, hh);\n" +
        "        }\n" +
        "    } else {\n" +
        "        if (P.x < -hw) {\n" +
        "            groundCol = sampleLeft(P, hw, hh);\n" +
        "        } else if (P.x > hw) {\n" +
        "            groundCol = sampleRight(P, hw, hh);\n" +
        "        } else {\n" +
        "            // Under car shadow\n" +
        "            groundCol = vec4(0.02, 0.02, 0.03, 1.0);\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // Calibration Grid Overlay (Draw reference distance lines & seams if enabled)\n" +
        "    if (uShowGrid == 1 && !(abs(P.x) <= hw && abs(P.y) <= hh)) {\n" +
        "        float dX = max(0.0, abs(P.x) - hw);\n" +
        "        float dY = max(0.0, abs(P.y) - hh);\n" +
        "        float distPerim = max(dX, dY);\n" +
        "        float gStep = 0.14;\n" +
        "        float mVal = mod(distPerim, gStep);\n" +
        "        if (mVal < 0.004 || mVal > (gStep - 0.004)) {\n" +
        "            groundCol = mix(groundCol, vec4(0.15, 0.85, 1.0, 1.0), 0.70);\n" +
        "        }\n" +
        "        if (abs(P.x) < 0.003) {\n" +
        "            groundCol = mix(groundCol, vec4(0.95, 0.25, 0.25, 1.0), 0.60);\n" +
        "        }\n" +
        "    }\n" +
        "\n" +
        "    // Ambient drop shadow around car perimeter\n" +
        "    float dOutX = max(0.0, abs(P.x) - hw);\n" +
        "    float dOutY = max(0.0, abs(P.y) - hh);\n" +
        "    float dCar = length(vec2(dOutX, dOutY));\n" +
        "    if (dCar > 0.0 && dCar < 0.035) {\n" +
        "        float shadowAlpha = (1.0 - smoothstep(0.0, 0.035, dCar)) * 0.55;\n" +
        "        groundCol.rgb = mix(groundCol.rgb, vec3(0.0, 0.0, 0.0), shadowAlpha);\n" +
        "    }\n" +
        "\n" +
        "    // Draw top-down car model with roof and sunroof\n" +
        "    if (abs(P.x) <= hw && abs(P.y) <= hh) {\n" +
        "        vec2 carUv = vec2((P.x / (hw * 2.0)) + 0.5, (P.y / (hh * 2.0)) + 0.5);\n" +
        "        vec4 carCol = texture2D(uCarTex, carUv);\n" +
        "        gl_FragColor = mix(groundCol, carCol, carCol.a);\n" +
        "    } else {\n" +
        "        gl_FragColor = groundCol;\n" +
        "    }\n" +
        "}\n";

    private final Renderer renderer;
    private SurfaceReadyListener readyListener;
    private volatile float cornerRadiusPx = 28.0f;

    // Vehicle tuning parameters
    private volatile float carWidth = 0.32f;
    private volatile float carHeight = 0.71f;
    private volatile float carOffsetX = 0.0f;
    private volatile float carOffsetY = 0.0f;
    private volatile float zoom = 1.0f;

    // Front Camera (calibrated default FOV width = 0.08f, fixes "too wide" issue)
    private volatile float fovFront = 0.08f;
    private volatile float depthFront = 0.06f;
    private volatile float distFront = 0.0f;
    private volatile float scaleFront = 1.0f;
    private volatile float pitchFront = 0.0f;
    private volatile boolean flipFrontV = true; // Front camera flipped vertically so it's upright
    private volatile boolean mirrorFront = false;

    // Rear Camera (calibrated default FOV width = 0.08f)
    private volatile float fovRear = 0.08f;
    private volatile float depthRear = 0.06f;
    private volatile float distRear = 0.0f;
    private volatile float scaleRear = 1.0f;
    private volatile float pitchRear = 0.0f;

    // Side Cameras
    private volatile float fovSide = 0.08f;
    private volatile float depthSide = 0.06f;
    private volatile float distSide = 0.0f;
    private volatile float scaleSide = 1.0f;
    private volatile float pitchSide = 0.0f;
    private volatile float fishSide = -0.15f;
    private volatile float rotSideDegL = 0.0f;
    private volatile float rotSideDegR = 0.0f;
    private volatile float sideCamOffsetY = -0.10f;
    private volatile boolean flipSideVL = false;
    private volatile boolean flipSideVR = false;
    private volatile boolean mirrorSideL = false;
    private volatile boolean mirrorSideR = false;

    // Seam & Visual
    private volatile float seamBlend = 1.0f;
    private volatile float seamAngleFront = 0.38f;
    private volatile boolean flipRearV = true;
    private volatile boolean mirrorRear = false;
    private volatile int carModelColor = 0; // 0 = Silver/Gray, 1 = White
    private volatile boolean showGrid = false; // Calibration grid overlay

    // Per-Camera Roll Tilt Calibration (มุมเอียงกล้องซ้าย/ขวา 4 ตัว)
    private volatile float rollFront = 0.0f;
    private volatile float rollRear = 0.0f;
    private volatile float rollLeft = 0.0f;
    private volatile float rollRight = 0.0f;

    public interface SurfaceReadyListener {
        void onReady(Surface surface);
    }

    public BevGLView(Context context) {
        this(context, null);
    }

    public BevGLView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setZOrderMediaOverlay(true);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        setPreserveEGLContextOnPause(true);

        this.renderer = new Renderer();
        setRenderer(this.renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void setSurfaceReadyListener(SurfaceReadyListener listener) {
        this.readyListener = listener;
    }

    public Surface getCameraSurface() {
        return this.renderer != null ? this.renderer.camSurface : null;
    }

    public void setCornerRadiusPx(float radiusPx) {
        this.cornerRadiusPx = radiusPx;
        requestRender();
    }

    public void setZoom(float zoom) {
        this.zoom = Math.max(0.4f, Math.min(2.5f, zoom));
        requestRender();
    }

    public float getZoom() { return this.zoom; }

    public void setCarDimensions(float width, float height) {
        this.carWidth = width;
        this.carHeight = height;
        requestRender();
    }

    public float getCarWidth() { return this.carWidth; }
    public float getCarHeight() { return this.carHeight; }

    public void setCarOffsetY(float offsetY) {
        this.carOffsetY = offsetY;
        requestRender();
    }

    public float getCarOffsetY() { return this.carOffsetY; }

    // Front Camera Controls
    public float getFovFront() { return this.fovFront; }
    public void setFovFront(float v) { this.fovFront = Math.max(0.01f, v); requestRender(); }

    public float getDepthFront() { return this.depthFront; }
    public void setDepthFront(float v) { this.depthFront = Math.max(0.01f, v); requestRender(); }

    public float getDistFront() { return this.distFront; }
    public void setDistFront(float v) { this.distFront = v; requestRender(); }

    public float getScaleFront() { return this.scaleFront; }
    public void setScaleFront(float v) { this.scaleFront = v; requestRender(); }

    public float getPitchFront() { return this.pitchFront; }
    public void setPitchFront(float v) { this.pitchFront = v; requestRender(); }

    public boolean isFlipFrontV() { return this.flipFrontV; }
    public void setFlipFrontV(boolean flip) { this.flipFrontV = flip; requestRender(); }

    public boolean isMirrorFront() { return this.mirrorFront; }
    public void setMirrorFront(boolean mirror) { this.mirrorFront = mirror; requestRender(); }

    // Rear Camera Controls
    public float getFovRear() { return this.fovRear; }
    public void setFovRear(float v) { this.fovRear = Math.max(0.01f, v); requestRender(); }

    public float getDepthRear() { return this.depthRear; }
    public void setDepthRear(float v) { this.depthRear = Math.max(0.01f, v); requestRender(); }

    public float getDistRear() { return this.distRear; }
    public void setDistRear(float v) { this.distRear = v; requestRender(); }

    public float getScaleRear() { return this.scaleRear; }
    public void setScaleRear(float v) { this.scaleRear = v; requestRender(); }

    public float getPitchRear() { return this.pitchRear; }
    public void setPitchRear(float v) { this.pitchRear = v; requestRender(); }

    // Side Camera Controls
    public float getFovSide() { return this.fovSide; }
    public void setFovSide(float v) { this.fovSide = Math.max(0.01f, v); requestRender(); }

    public float getDepthSide() { return this.depthSide; }
    public void setDepthSide(float v) { this.depthSide = Math.max(0.01f, v); requestRender(); }

    public float getDistSide() { return this.distSide; }
    public void setDistSide(float v) { this.distSide = v; requestRender(); }

    public float getScaleSide() { return this.scaleSide; }
    public void setScaleSide(float v) { this.scaleSide = v; requestRender(); }

    public float getPitchSide() { return this.pitchSide; }
    public void setPitchSide(float v) { this.pitchSide = v; requestRender(); }

    public float getFishSide() { return this.fishSide; }
    public void setFishSide(float v) { this.fishSide = v; requestRender(); }

    public float getRotSideDegL() { return this.rotSideDegL; }
    public void setRotSideDegL(float deg) { this.rotSideDegL = deg; requestRender(); }

    public float getRotSideDegR() { return this.rotSideDegR; }
    public void setRotSideDegR(float deg) { this.rotSideDegR = deg; requestRender(); }

    public void cycleRotLeft() {
        float cur = Math.round(this.rotSideDegL / 90.0f) * 90.0f;
        float next = (cur + 90.0f) % 360.0f;
        if (next < 0) next += 360.0f;
        setRotSideDegL(next);
    }

    public void cycleRotRight() {
        float cur = Math.round(this.rotSideDegR / 90.0f) * 90.0f;
        float next = (cur + 90.0f) % 360.0f;
        if (next < 0) next += 360.0f;
        setRotSideDegR(next);
    }

    public float getSideCamOffsetY() { return this.sideCamOffsetY; }
    public void setSideCamOffsetY(float y) { this.sideCamOffsetY = y; requestRender(); }

    public boolean isFlipSideVL() { return this.flipSideVL; }
    public void setFlipSideVL(boolean b) { this.flipSideVL = b; requestRender(); }

    public boolean isFlipSideVR() { return this.flipSideVR; }
    public void setFlipSideVR(boolean b) { this.flipSideVR = b; requestRender(); }

    public boolean isMirrorSideL() { return this.mirrorSideL; }
    public void setMirrorSideL(boolean b) { this.mirrorSideL = b; requestRender(); }

    public boolean isMirrorSideR() { return this.mirrorSideR; }
    public void setMirrorSideR(boolean b) { this.mirrorSideR = b; requestRender(); }

    public float getSeamBlend() { return this.seamBlend; }
    public void setSeamBlend(float v) { this.seamBlend = v; requestRender(); }

    public float getSeamAngleFront() { return this.seamAngleFront; }
    public void setSeamAngleFront(float v) { this.seamAngleFront = Math.max(0.10f, v); requestRender(); }

    public void setFlipRearV(boolean flip) { this.flipRearV = flip; requestRender(); }
    public boolean isFlipRearV() { return this.flipRearV; }

    public void setMirrorRear(boolean mirror) { this.mirrorRear = mirror; requestRender(); }
    public boolean isMirrorRear() { return this.mirrorRear; }

    public boolean isShowGrid() { return this.showGrid; }
    public void setShowGrid(boolean show) { this.showGrid = show; requestRender(); }
    public void toggleGrid() { setShowGrid(!this.showGrid); }

    public void setCarModelColor(int colorIndex) {
        this.carModelColor = colorIndex;
        queueEvent(() -> {
            if (this.renderer != null) {
                this.renderer.updateCarTexture();
                requestRender();
            }
        });
    }

    public int getCarModelColor() { return this.carModelColor; }

    public void applyPreset(int presetId) {
        // 0 = Atto 3 / Yuan Plus, 1 = Dolphin, 2 = Seal, 3 = Standard BYD
        switch (presetId) {
            case 0: // Atto 3 / Yuan Plus (Authentic BYD SA20 Dimensions)
                this.carWidth = 0.32f;
                this.carHeight = 0.71f;
                this.fovFront = 0.080f;
                this.fovRear = 0.080f;
                this.fovSide = 0.080f;
                this.sideCamOffsetY = -0.10f;
                this.fishSide = -0.15f;
                this.seamAngleFront = 0.38f;
                this.seamBlend = 1.0f;
                break;
            case 1: // Dolphin
                this.carWidth = 0.30f;
                this.carHeight = 0.65f;
                this.fovFront = 0.075f;
                this.fovRear = 0.075f;
                this.fovSide = 0.075f;
                this.sideCamOffsetY = -0.09f;
                this.fishSide = -0.15f;
                this.seamAngleFront = 0.38f;
                this.seamBlend = 1.0f;
                break;
            case 2: // Seal
                this.carWidth = 0.31f;
                this.carHeight = 0.73f;
                this.fovFront = 0.085f;
                this.fovRear = 0.085f;
                this.fovSide = 0.085f;
                this.sideCamOffsetY = -0.12f;
                this.fishSide = -0.15f;
                this.seamAngleFront = 0.38f;
                this.seamBlend = 1.0f;
                break;
            default: // Standard Factory
                resetDefaults();
                return;
        }
        requestRender();
    }

    public void resetDefaults() {
        this.zoom = 1.0f;
        this.carWidth = 0.32f;
        this.carHeight = 0.68f;
        this.carOffsetX = 0.0f;
        this.carOffsetY = 0.0f;

        this.scaleFront = 0.40f;
        this.depthFront = 0.50f;
        this.distFront = 0.0f;
        this.pitchFront = 0.0f;
        this.flipFrontV = true;
        this.mirrorFront = false;

        this.scaleRear = 0.40f;
        this.depthRear = 0.50f;
        this.distRear = 0.0f;
        this.pitchRear = 0.0f;
        this.flipRearV = true;
        this.mirrorRear = false;

        this.scaleSide = 0.40f;
        this.depthSide = 0.50f;
        this.distSide = 0.0f;
        this.pitchSide = 0.0f;
        this.rotSideDegL = 270.0f;
        this.rotSideDegR = 90.0f;
        this.sideCamOffsetY = 0.0f;
        this.flipSideVL = true;
        this.flipSideVR = true;
        this.mirrorSideL = false;
        this.mirrorSideR = false;

        this.seamBlend = 1.0f;
        this.seamAngleFront = 1.0f;
        this.carModelColor = 0;
        this.showGrid = false;
        queueEvent(() -> {
            if (this.renderer != null) {
                this.renderer.updateCarTexture();
            }
        });
        requestRender();
    }

    public void loadFromPrefs(SharedPreferences prefs) {
        if (prefs == null) return;
        int version = prefs.getInt("bev_calib_version", 0);
        if (version < 3) {
            // Upgrade to Flat 2D Quad-View defaults
            resetDefaults();
            prefs.edit()
                .putInt("bev_calib_version", 3)
                .putFloat("bev_car_w", 0.32f)
                .putFloat("bev_car_h", 0.68f)
                .putFloat("bev_scale_front", 0.40f)
                .putFloat("bev_depth_front", 0.50f)
                .putFloat("bev_scale_rear", 0.40f)
                .putFloat("bev_depth_rear", 0.50f)
                .putFloat("bev_scale_side", 0.40f)
                .putFloat("bev_depth_side", 0.50f)
                .putFloat("bev_rot_side_l", 270.0f)
                .putFloat("bev_rot_side_r", 90.0f)
                .putBoolean("bev_flip_side_vl", true)
                .putBoolean("bev_flip_side_vr", true)
                .putBoolean("bev_flip_front", true)
                .putBoolean("bev_flip_rear", true)
                .apply();
            return;
        }

        this.zoom = prefs.getFloat("bev_zoom", 1.0f);
        this.carWidth = prefs.getFloat("bev_car_w", 0.32f);
        this.carHeight = prefs.getFloat("bev_car_h", 0.71f);
        this.carOffsetY = prefs.getFloat("bev_car_off_y", 0.0f);

        this.fovFront = prefs.getFloat("bev_fov_front", 0.080f);
        this.depthFront = prefs.getFloat("bev_depth_front", 0.060f);
        this.distFront = prefs.getFloat("bev_dist_front", 0.0f);
        this.scaleFront = prefs.getFloat("bev_scale_front", 1.0f);
        this.pitchFront = prefs.getFloat("bev_pitch_front", 0.0f);
        this.flipFrontV = prefs.getBoolean("bev_flip_front", true);
        this.mirrorFront = prefs.getBoolean("bev_mirror_front", false);

        this.fovRear = prefs.getFloat("bev_fov_rear", 0.080f);
        this.depthRear = prefs.getFloat("bev_depth_rear", 0.060f);
        this.distRear = prefs.getFloat("bev_dist_rear", 0.0f);
        this.scaleRear = prefs.getFloat("bev_scale_rear", 1.0f);
        this.pitchRear = prefs.getFloat("bev_pitch_rear", 0.0f);

        this.fovSide = prefs.getFloat("bev_fov_side", 0.080f);
        this.depthSide = prefs.getFloat("bev_depth_side", 0.060f);
        this.distSide = prefs.getFloat("bev_dist_side", 0.0f);
        this.scaleSide = prefs.getFloat("bev_scale_side", 1.0f);
        this.pitchSide = prefs.getFloat("bev_pitch_side", 0.0f);
        this.fishSide = prefs.getFloat("bev_fish_side", -0.15f);
        this.rotSideDegL = prefs.getFloat("bev_rot_side_l", 0.0f);
        this.rotSideDegR = prefs.getFloat("bev_rot_side_r", 0.0f);
        this.sideCamOffsetY = prefs.getFloat("bev_side_offset_y", -0.10f);
        this.flipSideVL = prefs.getBoolean("bev_flip_side_vl", false);
        this.flipSideVR = prefs.getBoolean("bev_flip_side_vr", false);
        this.mirrorSideL = prefs.getBoolean("bev_mirror_side_l", false);
        this.mirrorSideR = prefs.getBoolean("bev_mirror_side_r", false);

        this.seamBlend = prefs.getFloat("bev_seam_blend", 1.0f);
        this.seamAngleFront = prefs.getFloat("bev_seam_angle_front", 0.38f);
        this.flipRearV = prefs.getBoolean("bev_flip_rear", true);
        this.mirrorRear = prefs.getBoolean("bev_mirror_rear", false);
        this.carModelColor = prefs.getInt("bev_car_color", 0);

        this.rollFront = (float) Math.toRadians(prefs.getFloat("bev_angle_front", 0.0f));
        this.rollRear = (float) Math.toRadians(prefs.getFloat("bev_angle_rear", 0.0f));
        this.rollLeft = (float) Math.toRadians(prefs.getFloat("bev_angle_left", 0.0f));
        this.rollRight = (float) Math.toRadians(prefs.getFloat("bev_angle_right", 0.0f));
        this.showGrid = prefs.getBoolean("bev_show_grid", false);
        queueEvent(() -> {
            if (this.renderer != null) {
                this.renderer.updateCarTexture();
            }
        });
        requestRender();
    }

    public void saveToPrefs(SharedPreferences prefs) {
        if (prefs == null) return;
        prefs.edit()
            .putInt("bev_calib_version", 2)
            .putFloat("bev_zoom", this.zoom)
            .putFloat("bev_car_w", this.carWidth)
            .putFloat("bev_car_h", this.carHeight)
            .putFloat("bev_car_off_y", this.carOffsetY)
            .putFloat("bev_fov_front", this.fovFront)
            .putFloat("bev_depth_front", this.depthFront)
            .putFloat("bev_dist_front", this.distFront)
            .putFloat("bev_scale_front", this.scaleFront)
            .putFloat("bev_pitch_front", this.pitchFront)
            .putBoolean("bev_flip_front", this.flipFrontV)
            .putBoolean("bev_mirror_front", this.mirrorFront)
            .putFloat("bev_fov_rear", this.fovRear)
            .putFloat("bev_depth_rear", this.depthRear)
            .putFloat("bev_dist_rear", this.distRear)
            .putFloat("bev_scale_rear", this.scaleRear)
            .putFloat("bev_pitch_rear", this.pitchRear)
            .putFloat("bev_fov_side", this.fovSide)
            .putFloat("bev_depth_side", this.depthSide)
            .putFloat("bev_dist_side", this.distSide)
            .putFloat("bev_scale_side", this.scaleSide)
            .putFloat("bev_pitch_side", this.pitchSide)
            .putFloat("bev_fish_side", this.fishSide)
            .putFloat("bev_rot_side_l", this.rotSideDegL)
            .putFloat("bev_rot_side_r", this.rotSideDegR)
            .putFloat("bev_side_offset_y", this.sideCamOffsetY)
            .putBoolean("bev_flip_side_vl", this.flipSideVL)
            .putBoolean("bev_flip_side_vr", this.flipSideVR)
            .putBoolean("bev_mirror_side_l", this.mirrorSideL)
            .putBoolean("bev_mirror_side_r", this.mirrorSideR)
            .putFloat("bev_seam_blend", this.seamBlend)
            .putFloat("bev_seam_angle_front", this.seamAngleFront)
            .putBoolean("bev_flip_rear", this.flipRearV)
            .putBoolean("bev_mirror_rear", this.mirrorRear)
            .putInt("bev_car_color", this.carModelColor)
            .putBoolean("bev_show_grid", this.showGrid)
            .apply();
    }

    private static FloatBuffer fb(float[] arr) {
        FloatBuffer buf = ByteBuffer.allocateDirect(arr.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        buf.put(arr).position(0);
        return buf;
    }

    final class Renderer implements GLSurfaceView.Renderer {
        private int program;
        private int aPos;
        private int aUv;
        private int uCameraTex;
        private int uCarTex;
        private int uSt;
        private int uAspect;
        private int uCarSize;
        private int uCarOffset;
        private int uZoom;
        private int uRadius;
        private int uWindowSize;

        // Front
        private int uFovFront;
        private int uDepthFront;
        private int uDistFront;
        private int uScaleFront;
        private int uPitchFront;
        private int uFlipFrontV;
        private int uMirrorFront;

        // Rear
        private int uFovRear;
        private int uDepthRear;
        private int uDistRear;
        private int uScaleRear;
        private int uPitchRear;

        // Side
        private int uFovSide;
        private int uDepthSide;
        private int uDistSide;
        private int uScaleSide;
        private int uPitchSide;
        private int uFishSide;
        private int uRotSideL;
        private int uRotSideR;
        private int uSideCamOffsetY;
        private int uFlipSideVL;
        private int uFlipSideVR;
        private int uMirrorSideL;
        private int uMirrorSideR;

        // Common
        private int uSeamBlend;
        private int uSeamAngleFront;
        private int uFlipRearV;
        private int uMirrorRear;
        private int uShowGrid;

        // Roll Tilt
        private int uRollFront;
        private int uRollRear;
        private int uRollLeft;
        private int uRollRight;

        private int oesTexId;
        private int carTexId = -1;
        private SurfaceTexture camST;
        volatile Surface camSurface;

        private final float[] stMatrix = new float[16];
        private final FloatBuffer quadPos;
        private final FloatBuffer quadUv;
        private int viewW = 360;
        private int viewH = 640;
        private float aspect = 1.7778f;

        private Renderer() {
            this.quadPos = BevGLView.fb(new float[]{
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
            });
            this.quadUv = BevGLView.fb(new float[]{
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
            });
        }

        private void initOesTex(int texId) {
            GLES20.glBindTexture(36197, texId);
            GLES20.glTexParameteri(36197, 10242, 33071);
            GLES20.glTexParameteri(36197, 10243, 33071);
            GLES20.glTexParameteri(36197, 10241, 9729);
            GLES20.glTexParameteri(36197, 10240, 9729);
        }

        void updateCarTexture() {
            try {
                int resId = CarModelManager.getSelectedCarModelDrawable(getContext());
                Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId);
                if (bmp != null) {
                    if (this.carTexId == -1) {
                        int[] tex = new int[1];
                        GLES20.glGenTextures(1, tex, 0);
                        this.carTexId = tex[0];
                    }
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.carTexId);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, 10242, 33071);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, 10243, 33071);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, 10241, 9729);
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, 10240, 9729);
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
                    bmp.recycle();
                    Log.i(TAG, "Car texture loaded successfully (color=" + BevGLView.this.carModelColor + ")");
                }
            } catch (Throwable e) {
                Log.e(TAG, "Failed loading car texture", e);
            }
        }

        @Override
        public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
            this.program = buildProgram();
            this.aPos = GLES20.glGetAttribLocation(this.program, "aPos");
            this.aUv = GLES20.glGetAttribLocation(this.program, "aUv");
            this.uCameraTex = GLES20.glGetUniformLocation(this.program, "uCameraTex");
            this.uCarTex = GLES20.glGetUniformLocation(this.program, "uCarTex");
            this.uSt = GLES20.glGetUniformLocation(this.program, "uSt");
            this.uAspect = GLES20.glGetUniformLocation(this.program, "uAspect");
            this.uCarSize = GLES20.glGetUniformLocation(this.program, "uCarSize");
            this.uCarOffset = GLES20.glGetUniformLocation(this.program, "uCarOffset");
            this.uZoom = GLES20.glGetUniformLocation(this.program, "uZoom");
            this.uRadius = GLES20.glGetUniformLocation(this.program, "uRadius");
            this.uWindowSize = GLES20.glGetUniformLocation(this.program, "uWindowSize");

            this.uFovFront = GLES20.glGetUniformLocation(this.program, "uFovFront");
            this.uDepthFront = GLES20.glGetUniformLocation(this.program, "uDepthFront");
            this.uDistFront = GLES20.glGetUniformLocation(this.program, "uDistFront");
            this.uScaleFront = GLES20.glGetUniformLocation(this.program, "uScaleFront");
            this.uPitchFront = GLES20.glGetUniformLocation(this.program, "uPitchFront");
            this.uFlipFrontV = GLES20.glGetUniformLocation(this.program, "uFlipFrontV");
            this.uMirrorFront = GLES20.glGetUniformLocation(this.program, "uMirrorFront");

            this.uFovRear = GLES20.glGetUniformLocation(this.program, "uFovRear");
            this.uDepthRear = GLES20.glGetUniformLocation(this.program, "uDepthRear");
            this.uDistRear = GLES20.glGetUniformLocation(this.program, "uDistRear");
            this.uScaleRear = GLES20.glGetUniformLocation(this.program, "uScaleRear");
            this.uPitchRear = GLES20.glGetUniformLocation(this.program, "uPitchRear");

            this.uFovSide = GLES20.glGetUniformLocation(this.program, "uFovSide");
            this.uDepthSide = GLES20.glGetUniformLocation(this.program, "uDepthSide");
            this.uDistSide = GLES20.glGetUniformLocation(this.program, "uDistSide");
            this.uScaleSide = GLES20.glGetUniformLocation(this.program, "uScaleSide");
            this.uPitchSide = GLES20.glGetUniformLocation(this.program, "uPitchSide");
            this.uFishSide = GLES20.glGetUniformLocation(this.program, "uFishSide");
            this.uRotSideL = GLES20.glGetUniformLocation(this.program, "uRotSideL");
            this.uRotSideR = GLES20.glGetUniformLocation(this.program, "uRotSideR");
            this.uSideCamOffsetY = GLES20.glGetUniformLocation(this.program, "uSideCamOffsetY");
            this.uFlipSideVL = GLES20.glGetUniformLocation(this.program, "uFlipSideVL");
            this.uFlipSideVR = GLES20.glGetUniformLocation(this.program, "uFlipSideVR");
            this.uMirrorSideL = GLES20.glGetUniformLocation(this.program, "uMirrorSideL");
            this.uMirrorSideR = GLES20.glGetUniformLocation(this.program, "uMirrorSideR");

            this.uSeamBlend = GLES20.glGetUniformLocation(this.program, "uSeamBlend");
            this.uSeamAngleFront = GLES20.glGetUniformLocation(this.program, "uSeamAngleFront");
            this.uFlipRearV = GLES20.glGetUniformLocation(this.program, "uFlipRearV");
            this.uMirrorRear = GLES20.glGetUniformLocation(this.program, "uMirrorRear");
            this.uShowGrid = GLES20.glGetUniformLocation(this.program, "uShowGrid");

            this.uRollFront = GLES20.glGetUniformLocation(this.program, "uRollFront");
            this.uRollRear = GLES20.glGetUniformLocation(this.program, "uRollRear");
            this.uRollLeft = GLES20.glGetUniformLocation(this.program, "uRollLeft");
            this.uRollRight = GLES20.glGetUniformLocation(this.program, "uRollRight");

            // OES Camera Texture
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            this.oesTexId = textures[0];
            initOesTex(this.oesTexId);

            this.camST = new SurfaceTexture(this.oesTexId);
            this.camST.setDefaultBufferSize(1920, 1080);
            this.camST.setOnFrameAvailableListener(surfaceTexture -> BevGLView.this.requestRender());
            this.camSurface = new Surface(this.camST);

            updateCarTexture();

            Log.i(TAG, "BevGLView onSurfaceCreated complete");
            if (BevGLView.this.readyListener != null) {
                final Surface surf = this.camSurface;
                BevGLView.this.post(() -> {
                    if (BevGLView.this.readyListener != null) {
                        BevGLView.this.readyListener.onReady(surf);
                    }
                });
            }
        }

        @Override
        public void onSurfaceChanged(GL10 gl10, int w, int h) {
            this.viewW = w;
            this.viewH = h;
            GLES20.glViewport(0, 0, w, h);
            if (w > 0 && h > 0) {
                this.aspect = (float) h / (float) w;
            }
        }

        @Override
        public void onDrawFrame(GL10 gl10) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

            if (this.camST != null) {
                try {
                    this.camST.updateTexImage();
                    this.camST.getTransformMatrix(this.stMatrix);
                } catch (Throwable ignored) {
                }
            }

            GLES20.glUseProgram(this.program);

            // Set uniforms
            GLES20.glUniformMatrix4fv(this.uSt, 1, false, this.stMatrix, 0);
            GLES20.glUniform1f(this.uAspect, this.aspect);
            GLES20.glUniform2f(this.uCarSize, BevGLView.this.carWidth, BevGLView.this.carHeight);
            GLES20.glUniform2f(this.uCarOffset, BevGLView.this.carOffsetX, BevGLView.this.carOffsetY);
            GLES20.glUniform1f(this.uZoom, BevGLView.this.zoom);
            GLES20.glUniform2f(this.uWindowSize, (float) this.viewW, (float) this.viewH);
            GLES20.glUniform1f(this.uRadius, BevGLView.this.cornerRadiusPx);

            // Front Camera
            GLES20.glUniform1f(this.uFovFront, BevGLView.this.fovFront);
            GLES20.glUniform1f(this.uDepthFront, BevGLView.this.depthFront);
            GLES20.glUniform1f(this.uDistFront, BevGLView.this.distFront);
            GLES20.glUniform1f(this.uScaleFront, BevGLView.this.scaleFront);
            GLES20.glUniform1f(this.uPitchFront, BevGLView.this.pitchFront);
            GLES20.glUniform1i(this.uFlipFrontV, BevGLView.this.flipFrontV ? 1 : 0);
            GLES20.glUniform1i(this.uMirrorFront, BevGLView.this.mirrorFront ? 1 : 0);

            // Rear Camera
            GLES20.glUniform1f(this.uFovRear, BevGLView.this.fovRear);
            GLES20.glUniform1f(this.uDepthRear, BevGLView.this.depthRear);
            GLES20.glUniform1f(this.uDistRear, BevGLView.this.distRear);
            GLES20.glUniform1f(this.uScaleRear, BevGLView.this.scaleRear);
            GLES20.glUniform1f(this.uPitchRear, BevGLView.this.pitchRear);

            // Side Cameras
            GLES20.glUniform1f(this.uFovSide, BevGLView.this.fovSide);
            GLES20.glUniform1f(this.uDepthSide, BevGLView.this.depthSide);
            GLES20.glUniform1f(this.uDistSide, BevGLView.this.distSide);
            GLES20.glUniform1f(this.uScaleSide, BevGLView.this.scaleSide);
            GLES20.glUniform1f(this.uPitchSide, BevGLView.this.pitchSide);
            GLES20.glUniform1f(this.uFishSide, BevGLView.this.fishSide);

            float radL = (float) Math.toRadians(BevGLView.this.rotSideDegL);
            float radR = (float) Math.toRadians(BevGLView.this.rotSideDegR);
            GLES20.glUniform1f(this.uRotSideL, radL);
            GLES20.glUniform1f(this.uRotSideR, radR);
            GLES20.glUniform1f(this.uSideCamOffsetY, BevGLView.this.sideCamOffsetY);
            GLES20.glUniform1i(this.uFlipSideVL, BevGLView.this.flipSideVL ? 1 : 0);
            GLES20.glUniform1i(this.uFlipSideVR, BevGLView.this.flipSideVR ? 1 : 0);
            GLES20.glUniform1i(this.uMirrorSideL, BevGLView.this.mirrorSideL ? 1 : 0);
            GLES20.glUniform1i(this.uMirrorSideR, BevGLView.this.mirrorSideR ? 1 : 0);

            // Common & Grid
            GLES20.glUniform1f(this.uSeamBlend, BevGLView.this.seamBlend);
            GLES20.glUniform1f(this.uSeamAngleFront, BevGLView.this.seamAngleFront);
            GLES20.glUniform1i(this.uFlipRearV, BevGLView.this.flipRearV ? 1 : 0);
            GLES20.glUniform1i(this.uMirrorRear, BevGLView.this.mirrorRear ? 1 : 0);
            GLES20.glUniform1i(this.uShowGrid, BevGLView.this.showGrid ? 1 : 0);

            // Per-Camera Roll Tilt
            GLES20.glUniform1f(this.uRollFront, BevGLView.this.rollFront);
            GLES20.glUniform1f(this.uRollRear, BevGLView.this.rollRear);
            GLES20.glUniform1f(this.uRollLeft, BevGLView.this.rollLeft);
            GLES20.glUniform1f(this.uRollRight, BevGLView.this.rollRight);

            // Bind Camera OES texture (unit 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(36197, this.oesTexId);
            GLES20.glUniform1i(this.uCameraTex, 0);

            // Bind Car 2D texture (unit 1)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this.carTexId);
            GLES20.glUniform1i(this.uCarTex, 1);

            // Draw full quad
            this.quadPos.position(0);
            GLES20.glEnableVertexAttribArray(this.aPos);
            GLES20.glVertexAttribPointer(this.aPos, 2, GLES20.GL_FLOAT, false, 0, (Buffer) this.quadPos);

            this.quadUv.position(0);
            GLES20.glEnableVertexAttribArray(this.aUv);
            GLES20.glVertexAttribPointer(this.aUv, 2, GLES20.GL_FLOAT, false, 0, (Buffer) this.quadUv);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glDisableVertexAttribArray(this.aPos);
            GLES20.glDisableVertexAttribArray(this.aUv);
        }

        private int buildProgram() {
            int vShader = compileShader(GLES20.GL_VERTEX_SHADER, BevGLView.VERT);
            int fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, BevGLView.FRAG);
            int prog = GLES20.glCreateProgram();
            GLES20.glAttachShader(prog, vShader);
            GLES20.glAttachShader(prog, fShader);
            GLES20.glLinkProgram(prog);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] == 0) {
                Log.e(TAG, "Link error: " + GLES20.glGetProgramInfoLog(prog));
            }
            return prog;
        }

        private int compileShader(int type, String source) {
            int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Compile error: " + GLES20.glGetShaderInfoLog(shader));
            }
            return shader;
        }
    }
}
