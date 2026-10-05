import { ArrowDown, ArrowRight, BookOpen, Camera, Download, Leaf, MapPin, ShieldCheck, Sparkles } from "lucide-react";
import WebsiteShell from "./components/WebsiteShell";
import Plans from "./components/Plans";

export const metadata = {
  title: "My Trail Log · Keep the little wonders",
  description: "A nature photo scrapbook for the discoveries along your path. Meet My Trail Log, explore plans, and open your private desktop journal.",
};
export default function Page() {
  return (
    <WebsiteShell>
      <section className="company-hero company-container">
        <div className="company-hero-copy">
          <p className="company-kicker"><Leaf size={15}/> A little more wonder. A little less hurry.</p>
          <h1>The best things on a walk<br/><em>aren’t measured.</em><br/>They’re noticed.</h1>
          <p className="company-intro">The flower by the gate. A tiny visitor. That tree you’ve passed a hundred times. Turn the things that catch your eye into a nature scrapbook that’s yours to keep.</p>
          <div className="company-actions"><a href="/journal" className="company-button company-button-primary">Open my journal <ArrowRight size={18}/></a><a href="#how-it-works" className="company-text-link">Meet My Trail Log <ArrowDown size={16}/></a></div>
          <div className="company-hero-note"><ShieldCheck size={18}/><span>Private by default. No ads. Your photos remain yours.</span></div>
        </div>
        <div className="company-hero-art">
          <img className="company-hero-woodland" src="/woodland.jpg" alt="Sunlight filtering through a green woodland above bluebells" width="1000" height="1300" fetchPriority="high"/>
          <span className="company-photo-note">Take the slower path.</span>
          <div className="company-phone"><img src="/company/journal-preview.webp" alt="My Trail Log’s native Android scrapbook, with photo cards, facts and a central camera button" width="1080" height="1920"/></div>
          <div className="company-hero-stamp"><Sparkles size={20}/><span>Small discoveries.<br/>Good memories.</span></div>
        </div>
      </section>

      <section id="how-it-works" className="company-section company-paper">
        <div className="company-container">
          <div className="company-section-heading"><p className="company-kicker">A journal, not a scoreboard</p><h2>Keep the moment.<br/><em>Find the story.</em></h2><p>Use the native Android app when you’re outdoors, then enjoy your journal on a bigger screen at home.</p></div>
          <div className="company-how-grid">
            <article><span className="company-step"><Camera size={25}/><b>01</b></span><h3>See something. Save it.</h3><p>One camera button, a photo and your discovery’s time and place. Save on your phone when there’s no signal; sync when you reconnect.</p></article>
            <article><span className="company-step"><Leaf size={25}/><b>02</b></span><h3>Get to know it.</h3><p>Smart AI suggests an identity and a short, interesting story. Uncertain photos can receive an automatic closer review within your plan’s allowance. Reference links help you explore further; you can correct a suggestion.</p></article>
            <article><span className="company-step"><BookOpen size={25}/><b>03</b></span><h3>Make a little collection.</h3><p>A scrapbook by day, a map of things you’ve noticed, and achievements that celebrate curiosity. Share only the discoveries you choose.</p></article>
          </div>
        </div>
      </section>

      <section className="company-section company-container company-feature-story">
        <div className="company-nature-collage"><img className="company-robin" src="/field-robin.jpg" alt="A robin perched outdoors" width="600" height="800" loading="lazy"/><img className="company-fox" src="/field-fox.jpg" alt="A fox surrounded by green plants" width="500" height="600" loading="lazy"/><span className="company-collage-note">What will you notice next?</span></div>
        <div><p className="company-kicker">Your own small corner of the outdoors</p><h2>A place for your discoveries.<br/><em>And the people who get it.</em></h2><p>Give a favourite photo an acorn. Follow a friend’s discoveries. Keep a place to check out later. Your real portrait stays private; your community profile uses an illustrated avatar and a username.</p><div className="company-feature-list"><p><MapPin size={20}/><span>Explore photo pins by place, subject or acorns.</span></p><p><ShieldCheck size={20}/><span>Choose private, local, invited people or everyone signed in.</span></p><p><Download size={20}/><span>Download your photos and journal data for free, on every plan.</span></p></div><a className="company-text-link" href="/community-rules">Read the sharing guidelines <ArrowRight size={17}/></a></div>
      </section>

      <section className="company-section company-sage" id="plans"><div className="company-container"><div className="company-section-heading"><p className="company-kicker">A little room. Or a whole field.</p><h2>Choose room for<br/><em>your next discoveries.</em></h2><p>Start with a free journal. Your existing photos and free downloads stay available whichever plan you use.</p></div><Plans compact/><p className="company-pricing-footnote">Paid subscriptions will open when Google Play setup is complete. <a href="/pricing">Compare plans, allowances and cancellation details.</a></p></div></section>

      <section className="company-section company-container company-about" id="about"><div><p className="company-kicker">Made for noticing</p><h2>Thinking about<br/><em>the little things.</em></h2></div><div><p>My Trail Log is an independent project by Nathan Tracey. The idea is simple: walks are full of things worth remembering, and a photo can be the start of getting to know them.</p><p>“Thinking About Ltd” is the proposed company name. The company has not yet been incorporated; Nathan Tracey currently operates the service. We’ll update our legal details when that changes.</p><a href="/support" className="company-text-link">Get in touch <ArrowRight size={17}/></a></div></section>
    </WebsiteShell>
  );
}
