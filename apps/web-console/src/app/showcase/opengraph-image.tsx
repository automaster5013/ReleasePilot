import { ImageResponse } from "next/og";

export const alt = "ReleasePilot — Progressive Delivery Control Plane";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          padding: "64px 72px",
          color: "#f7f8fb",
          background: "linear-gradient(135deg, #07090d 0%, #101827 58%, #10263a 100%)",
          fontFamily: "Arial, sans-serif",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 20, fontSize: 32, fontWeight: 700 }}>
            <div
              style={{
                width: 64,
                height: 64,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                borderRadius: 18,
                color: "#08111d",
                background: "#70e1b5",
                fontSize: 25,
                fontWeight: 900,
              }}
            >
              RP
            </div>
            ReleasePilot
          </div>
          <div style={{ color: "#70e1b5", fontSize: 20, letterSpacing: 3 }}>PROGRESSIVE DELIVERY</div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 22 }}>
          <div style={{ maxWidth: 950, display: "flex", flexDirection: "column", fontSize: 70, lineHeight: 1.05, fontWeight: 800, letterSpacing: -3 }}>
            <span>Release decisions,</span>
            <span style={{ color: "#70e1b5" }}>made safe.</span>
          </div>
          <div style={{ color: "#aebbd0", fontSize: 28 }}>Approval, canary traffic, metric gates and automatic rollback in one control plane.</div>
        </div>

        <div style={{ display: "flex", gap: 16 }}>
          {["Approval review", "Traffic control", "Automatic rollback"].map((label, index) => (
            <div
              key={label}
              style={{
                display: "flex",
                alignItems: "center",
                gap: 12,
                padding: "14px 20px",
                border: "1px solid #33445d",
                borderRadius: 999,
                background: "rgba(17, 25, 39, 0.82)",
                color: "#dce4ef",
                fontSize: 20,
              }}
            >
              <span style={{ color: index === 2 ? "#ff9b9b" : "#70e1b5" }}>0{index + 1}</span>
              {label}
            </div>
          ))}
        </div>
      </div>
    ),
    size,
  );
}
