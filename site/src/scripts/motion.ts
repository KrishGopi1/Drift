// Motion for the site. Progressive enhancement: the page is complete and
// readable before this runs; motion only adds. Under reduced motion every
// function here leaves the static page alone.
import { gsap } from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

export const reducedMotion = () => matchMedia("(prefers-reduced-motion: reduce)").matches;

/* ---------- Reveal: SVG strokes draw, groups stagger in, once ---------- */

export function initReveals(root: ParentNode = document) {
  if (reducedMotion()) return;
  const onceVisible = (target: Element, run: () => void) => {
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        observer.disconnect();
        if (!reducedMotion()) run();
      }
    }, { rootMargin: "0px 0px -20% 0px" });
    observer.observe(target);
  };
  root.querySelectorAll<SVGGeometryElement>("[data-draw]").forEach((el) => {
    onceVisible(el.closest("[data-draw-root]") ?? el, () => {
    const len = el.getTotalLength();
    gsap.fromTo(
      el,
      { strokeDasharray: len, strokeDashoffset: len },
      {
        strokeDashoffset: 0,
        duration: Number(el.dataset.draw) || 1.2,
        ease: "power2.inOut",
      },
    );
    });
  });
  root.querySelectorAll<HTMLElement>("[data-stagger]").forEach((group) => {
    onceVisible(group, () => {
    gsap.from(group.children, {
      opacity: 0,
      y: 18,
      duration: 0.7,
      ease: "power3.out",
      stagger: Number(group.dataset.stagger) || 0.08,
    });
    });
  });
}

/* ---------- Steps: sticky visual + scrolling text, native sticky + IO ---------- */

export function initSteps(root: ParentNode = document) {
  root.querySelectorAll<HTMLElement>("[data-steps]").forEach((el) => {
    const steps = [...el.querySelectorAll<HTMLElement>("[data-step]")];
    const set = (i: number) => {
      el.dataset.active = String(i);
      steps.forEach((s, j) => s.toggleAttribute("data-current", i === j));
    };
    set(0);
    const io = new IntersectionObserver(
      (entries) => {
        for (const e of entries) if (e.isIntersecting) set(steps.indexOf(e.target as HTMLElement));
      },
      { rootMargin: "-45% 0px -45% 0px" },
    );
    steps.forEach((s) => io.observe(s));
  });
}

/* ---------- Count: numbers tick up once when seen ---------- */

export function initCounts(root: ParentNode = document) {
  if (reducedMotion()) return;
  const fmt = new Intl.NumberFormat("en");
  root.querySelectorAll<HTMLElement>("[data-count]").forEach((el) => {
    const end = Number(el.dataset.count);
    const state = { v: 0 };
    gsap.to(state, {
      v: end,
      duration: 1.4,
      ease: "power2.out",
      scrollTrigger: { trigger: el, start: "top 85%", once: true },
      onUpdate: () => (el.textContent = fmt.format(Math.round(state.v))),
    });
  });
}

export { gsap, ScrollTrigger };
