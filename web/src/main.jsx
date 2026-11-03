import React from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";

function Icon({ name, size = 20 }) {
  const shared = {
    width: size,
    height: size,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 1.7,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    "aria-hidden": true,
  };

  if (name === "arrow") return <svg {...shared}><path d="M7 17 17 7M7 7h10v10" /></svg>;
  if (name === "download") return <svg {...shared}><path d="M12 3v12m-5-5 5 5 5-5M4 17v3h16v-3" /></svg>;
  if (name === "camera") return <svg {...shared}><path d="M4 7h3l1.5-2h7L17 7h3v12H4z" /><circle cx="12" cy="13" r="3.5" /></svg>;
  if (name === "play") return <svg {...shared}><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m10 9 5 3-5 3z" /></svg>;
  if (name === "message") return <svg {...shared}><path d="M20 11.5a7.5 7.5 0 0 1-7.5 7.5H6l-3 2v-6a7.5 7.5 0 1 1 17-3.5Z" /></svg>;
  if (name === "lock") return <svg {...shared}><rect x="4" y="10" width="16" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3m-4 5v2" /></svg>;
  return null;
}

function Brand({ compact = false }) {
  return (
    <a className={`brand${compact ? " brand--compact" : ""}`} href="#home" aria-label="Keepsake home">
      <span className="brand__mark" aria-hidden="true">
        <svg viewBox="0 0 108 108" role="presentation">
          <path fill="#31483B" d="M0 0h108v108H0z" />
          <path fill="#F0A08E" d="M27 21h12v27l25-27h16L52 51l30 36H66L44 59l-5 5v23H27z" />
          <path fill="#31483B" d="M39 48h13v13H39z" />
          <circle cx="83" cy="20" r="5" fill="#E9C47F" />
        </svg>
      </span>
      <span className="brand__name">keepsake<span className="brand__dot">.</span></span>
    </a>
  );
}

function FlowerIllustration({ className = "" }) {
  return (
    <svg
      className={`flower-illustration ${className}`}
      viewBox="0 0 160 185"
      fill="none"
      aria-hidden="true"
    >
      <path className="flower-illustration__stem" d="M78 77c5 31-4 58-19 88" />
      <path className="flower-illustration__leaf" d="M70 126c-20-19-33-10-13 6 6 1 11-1 13-6Z" />
      <path className="flower-illustration__leaf" d="M69 145c15-20 29-15 12 3-5 3-10 2-12-3Z" />
      <path className="flower-illustration__leaf" d="M69 164c-13-14-23-8-10 4 5 1 8 0 10-4Z" />
      <g transform="translate(82 65)">
        {[0, 60, 120, 180, 240, 300].map((angle) => (
          <path
            key={angle}
            className="flower-illustration__petal"
            transform={`rotate(${angle})`}
            d="M0 5C-7-2-14-9-10-18c3-8 12-10 16-2 5 8 0 17-6 25Z"
          />
        ))}
        <circle className="flower-illustration__center" r="6" />
      </g>
      <circle className="flower-illustration__speck" cx="30" cy="33" r="3" />
      <circle className="flower-illustration__speck flower-illustration__speck--second" cx="135" cy="85" r="4" />
    </svg>
  );
}

function DownloadButton({ light = false }) {
  return (
    <a className={`button button--download${light ? " button--light" : ""}`} href="/Keepsake.apk" download>
      <Icon name="download" size={18} />
      <span>Download for Android</span>
    </a>
  );
}

function PhonePreview() {
  return (
    <div className="phone" aria-label="Keepsake private photo journal preview">
      <div className="phone__frame">
        <div className="phone__status"><span>9:41</span><span className="phone__signal">●●●  ▰</span></div>
        <div className="phone__appbar">
          <div className="phone__app-icon" aria-hidden="true">
            <svg viewBox="0 0 108 108">
              <path fill="#31483B" d="M0 0h108v108H0z" />
              <path fill="#F0A08E" d="M27 21h12v27l25-27h16L52 51l30 36H66L44 59l-5 5v23H27z" />
              <path fill="#31483B" d="M39 48h13v13H39z" />
              <circle cx="83" cy="20" r="5" fill="#E9C47F" />
            </svg>
          </div>
          <div><strong>Keepsake</strong><small>YOURS ALONE</small></div>
          <div className="phone__avatar">K</div>
        </div>
        <div className="phone__intro">
          <span className="phone__eyebrow">YOUR PRIVATE ALBUM</span>
          <h2>Your life,<br />at your pace.</h2>
          <p>Photos and moments. Just for you.</p>
        </div>
        <div className="phone__actions">
          <div><span>TAKE A PHOTO</span><b>Open the camera</b></div>
          <div><span>SAVE A MEMORY</span><b>Choose from your phone</b></div>
        </div>
        <div className="memory">
          <div className="memory__photo">
            <div className="memory__sun" />
            <div className="memory__hills"><i /><i /><i /></div>
            <div className="memory__label">SUMMER<br />ON THE WEST COAST</div>
          </div>
          <div className="memory__caption"><b>Your memories</b><span>ON DEVICE</span></div>
          <div className="memory__details">A slow morning by the sea.</div>
          <div className="memory__comment"><span>Comments&nbsp; 2</span><span>VIEW ALL</span></div>
        </div>
        <div className="phone__home" />
      </div>
      <div className="preview-note">A little corner of your own</div>
    </div>
  );
}

const features = [
  { icon: "camera", title: "Capture the moment", body: "Take a photo or record a video right in the app. You can also choose something already on your phone." },
  { icon: "play", title: "Tell your story", body: "Add a caption to your photos and videos. Leave your own comments, now or later." },
  { icon: "lock", title: "Entirely your own", body: "Your posts stay on your device. No account, no uploads, and no audience." },
];

function App() {
  return (
    <>
      <header className="site-header" id="home">
        <div className="container site-header__inner">
          <Brand />
          <nav aria-label="Main navigation">
            <a href="#how-it-works">How it works</a>
            <a href="#privacy">Privacy</a>
          </nav>
          <a className="header-download" href="/Keepsake.apk" download>
            <span>Download</span><Icon name="arrow" size={16} />
          </a>
        </div>
      </header>

      <main>
        <section className="hero">
          <div className="container hero__grid">
            <div className="hero__copy">
              <div className="eyebrow">YOUR PRIVATE PHOTO JOURNAL</div>
              <h1>All worth<br />remembering.<br /><span>Yours alone.</span></h1>
              <p className="hero__description">Photos, videos, and your own words. A personal journal that lives on your phone, and nowhere else.</p>
              <div className="hero__actions">
                <DownloadButton />
                <a className="text-link" href="#how-it-works">Explore the app <Icon name="arrow" size={16} /></a>
              </div>
              <div className="hero__footnote"><span>Android 8.0 and up</span><span className="footnote-divider" /> Free to download</div>
            </div>
            <div className="hero__visual">
              <div className="hero__orbit hero__orbit--one" />
              <div className="hero__orbit hero__orbit--two" />
              <FlowerIllustration className="hero__flower" />
              <div className="hero__spark hero__spark--one">K</div>
              <div className="hero__spark hero__spark--two">LOCAL BY DESIGN</div>
              <PhonePreview />
            </div>
          </div>
          <div className="hero__bottom-line" />
        </section>

        <section className="manifesto" id="privacy">
          <div className="container manifesto__inner">
            <div className="manifesto__label"><span className="section-label">PRIVACY</span></div>
            <div className="manifesto__copy">
              <h2>Your memories belong to <span>you.</span></h2>
              <p>Keepsake is not a social network. There are no followers, no one else's feed, and no servers receiving your photos. It is your own private photo journal.</p>
            </div>
            <div className="privacy-badge"><Icon name="lock" size={19} /><span>STORED LOCALLY<br />ON YOUR DEVICE</span></div>
            <FlowerIllustration className="manifesto__flower" />
          </div>
        </section>

        <section className="features" id="how-it-works">
          <div className="container">
            <div className="features__heading">
              <div><span className="section-label">SIMPLE BY DESIGN</span><h2>Your moments.<br />Your way.</h2></div>
              <p>Everything you need to keep your memories together. Nothing more, nothing less.</p>
            </div>
            <div className="feature-grid">
              {features.map((feature) => (
                <article className="feature-card" key={feature.title}>
                  <div className="feature-card__top"><Icon name={feature.icon} size={22} /></div>
                  <h3>{feature.title}</h3>
                  <p>{feature.body}</p>
                </article>
              ))}
            </div>
          </div>
        </section>

        <section className="download-section">
          <div className="container download-panel">
            <div className="download-panel__orb" />
            <FlowerIllustration className="download-panel__flower" />
            <div className="download-panel__content">
              <span className="section-label">YOUR NEXT CHAPTER</span>
              <h2>Make room for<br />what matters.</h2>
              <p>Install Keepsake on your Android phone and create your first private post.</p>
              <DownloadButton light />
              <span className="download-panel__fineprint">Android 8.0 or later · APK download · Version 1.0.0</span>
            </div>
            <div className="download-panel__monogram" aria-hidden="true">K</div>
          </div>
        </section>
      </main>

      <footer className="site-footer">
        <div className="container site-footer__inner">
          <Brand compact />
          <span className="site-footer__note">A private photo journal. No account required.</span>
          <a href="#privacy">Privacy details <Icon name="arrow" size={14} /></a>
          <span className="site-footer__copyright">© 2026 Keepsake</span>
        </div>
      </footer>
    </>
  );
}

createRoot(document.getElementById("root")).render(
  <React.StrictMode><App /></React.StrictMode>,
);
