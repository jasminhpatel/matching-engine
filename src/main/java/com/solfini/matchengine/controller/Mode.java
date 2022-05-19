package com.solfini.matchengine.controller;

/**
 * The Mode enumeration lists the states that the matching engine may be in.
 */
public enum Mode {
  NONE,           // Undefined status, default at startup
  PRIMARY,        // Matching engine is in primary mode
  SECONDARY,      // Matching engine is in secondary (DR) mode
  SUSPENDED       // Matching engine is suspended
}
