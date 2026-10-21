"use client";
import { MotionConfig } from "motion/react";
/** Motion (Magic UI) animations follow the OS reduced-motion setting. */
export const Motion = ({ children }: { children: React.ReactNode }) => <MotionConfig reducedMotion="user">{children}</MotionConfig>;
