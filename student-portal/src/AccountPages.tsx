import {
    ArrowLeft,
    ArrowRight,
    CircleHelp,
    Fingerprint,
    GraduationCap,
    LockKeyhole,
    ShieldCheck,
} from "lucide-react";
import { student } from "./demo";
import { Brand, PageIntro, Panel, SectionHeading } from "./ui";

export function Profile() {
    return (
        <>
            <PageIntro
                eyebrow="YOUR SPACE"
                title="Profile & security"
                description="A preview of the personal and account settings experience."
            />
            <div className="profile-layout">
                <Panel className="profile-card">
                    <div className="profile-banner">
                        <span className="profile-avatar">
                            {student.initials}
                        </span>
                    </div>
                    <h2>{student.name}</h2>
                    <p>{student.program}</p>
                    <span className="profile-chip">{student.year}</span>
                    <div className="profile-divider" />
                    <dl>
                        <div>
                            <dt>Student ID</dt>
                            <dd>{student.id}</dd>
                        </div>
                        <div>
                            <dt>University</dt>
                            <dd>{student.university}</dd>
                        </div>
                        <div>
                            <dt>Email</dt>
                            <dd>{student.email}</dd>
                        </div>
                    </dl>
                    <p className="muted-note">
                        Fictional profile · changes are unavailable in this
                        visual prototype.
                    </p>
                </Panel>
                <div className="profile-stack">
                    <Panel>
                        <SectionHeading
                            label="ACCOUNT"
                            title="Security preview"
                        />
                        <div className="security-row">
                            <span className="stat-icon violet">
                                <LockKeyhole size={19} />
                            </span>
                            <div>
                                <strong>Sign-in experience</strong>
                                <p>
                                    Explore the visual login flow. No
                                    credentials are checked.
                                </p>
                            </div>
                            <a className="button-secondary" href="#/login">
                                Preview <ArrowRight size={15} />
                            </a>
                        </div>
                        <div className="security-row">
                            <span className="stat-icon blue">
                                <Fingerprint size={19} />
                            </span>
                            <div>
                                <strong>Multi-factor verification</strong>
                                <p>
                                    See the future verification screen without
                                    submitting a code.
                                </p>
                            </div>
                            <a className="button-secondary" href="#/mfa">
                                Preview <ArrowRight size={15} />
                            </a>
                        </div>
                    </Panel>
                    <Panel className="privacy-panel">
                        <ShieldCheck size={24} />
                        <div>
                            <h2>Built for a clear boundary</h2>
                            <p>
                                This prototype contains only fictional academic
                                data. It has no account, authentication,
                                database, or server connection.
                            </p>
                        </div>
                    </Panel>
                </div>
            </div>
        </>
    );
}

export function AuthPreview({ mfa }: { mfa: boolean }) {
    return (
        <div className="auth-page">
            <div className="auth-form-side">
                <Brand />
                <div className="auth-center">
                    <span className="eyebrow">
                        VISUAL PREVIEW · NO AUTHENTICATION
                    </span>
                    <h1>
                        {mfa ? "One more step." : "Your learning space awaits."}
                    </h1>
                    <p className="auth-lede">
                        {mfa
                            ? "This is a preview of a future verification screen. No code is sent or checked."
                            : "A quiet place to follow your academic journey. This screen does not sign you in."}
                    </p>
                    {mfa ? (
                        <div className="auth-card">
                            <label htmlFor="mfa-code">
                                Verification code preview
                            </label>
                            <input
                                id="mfa-code"
                                type="text"
                                inputMode="numeric"
                                maxLength={6}
                                placeholder="000000"
                                autoComplete="off"
                            />
                            <p>
                                For display only. Entering a code has no effect.
                            </p>
                            <a
                                className="button-primary auth-button"
                                href="#/dashboard"
                            >
                                Open fictional workspace demo{" "}
                                <ArrowRight size={17} />
                            </a>
                            <a className="auth-back" href="#/login">
                                <ArrowLeft size={15} /> Back to login preview
                            </a>
                        </div>
                    ) : (
                        <div className="auth-card">
                            <label htmlFor="email">Student email preview</label>
                            <input
                                id="email"
                                type="email"
                                placeholder="name@example.edu"
                                autoComplete="off"
                            />
                            <label htmlFor="password">Password preview</label>
                            <input
                                id="password"
                                type="password"
                                placeholder="••••••••"
                                autoComplete="off"
                            />
                            <p>
                                These fields are visual only and are never
                                submitted.
                            </p>
                            <a
                                className="button-primary auth-button"
                                href="#/mfa"
                            >
                                Preview MFA screen <ArrowRight size={17} />
                            </a>
                            <a className="auth-back" href="#/dashboard">
                                Open fictional workspace demo{" "}
                                <ArrowRight size={15} />
                            </a>
                        </div>
                    )}
                    <p className="auth-disclaimer">
                        <ShieldCheck size={15} /> Fictional demo · no account
                        session or security claim
                    </p>
                </div>
                <span className="auth-footer">
                    © 2026 Professor OS · Student Portal concept
                </span>
            </div>
            <div className="auth-art-side">
                <div className="auth-art-rings" aria-hidden="true">
                    <div />
                    <div />
                    <div />
                    <span>
                        <GraduationCap size={95} strokeWidth={1} />
                    </span>
                </div>
                <div className="auth-art-copy">
                    <span className="eyebrow">
                        PROFESSOR OS / STUDENT SPACE
                    </span>
                    <h2>
                        Make room for
                        <br />
                        <em>what's next.</em>
                    </h2>
                    <p>
                        One beautiful view of your courses, progress, and
                        possibilities.
                    </p>
                </div>
                <span className="auth-art-index">01 — 04</span>
            </div>
        </div>
    );
}

export function NotFound() {
    return (
        <div className="state-card">
            <span className="state-icon">
                <CircleHelp size={25} />
            </span>
            <h1>Page not found</h1>
            <p>This preview page doesn’t exist.</p>
            <a className="button-secondary" href="#/dashboard">
                Back to overview <ArrowRight size={16} />
            </a>
        </div>
    );
}
