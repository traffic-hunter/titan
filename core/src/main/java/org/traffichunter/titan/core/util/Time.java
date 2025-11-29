package org.traffichunter.titan.core.util;

public class Time {

  private static final long START_TIME = System.nanoTime();

  public static long currentNanos() {
    return System.nanoTime() - START_TIME;
  }
}