package com.solfini.matchengine.persist;

import com.solfini.instrument.Position;

public class Test {
  public static void test(final Position[] positions, final Position[] prevPositions) {
    if (positions == null || prevPositions == null) {
      // position.getQuantity()
    } else {
      int i = 0;
      int j = 0;
      Position position = null;
      Position prevPosition = null;
      while (true) {

        if (positions.length > i)
          position = positions[i];
        else
          position = null;
        if (prevPositions.length > j)
          prevPosition = prevPositions[j];
        else
          prevPosition = null;
        if (position == null && prevPosition == null)
          break;

        if (prevPosition == null) {
          System.out.println("skip position=" + position.getInstrumentId());
          i++;
          break;
        }
        if (position == null) {
          System.out.println("skip prevPosition=" + prevPosition.getInstrumentId());
          j++;
          break;
        }

        if (position.getInstrumentId() == prevPosition.getInstrumentId()) {
          System.out.println("match position=" + position.getInstrumentId() + ", prevPosition=" + prevPosition.getInstrumentId());
          i++;
          j++;
        } else if (position.getInstrumentId() < prevPosition.getInstrumentId()) {
          if (positions.length > i + 1)
            System.out.println("skip position=" + position.getInstrumentId());
          i++;
        } else {
          if (prevPositions.length > j + 1)
            System.out.println("skip position=" + prevPosition.getInstrumentId());
          j++;
        }


      }
    }
    System.out.println("done");
  }

  public static void test1() {
    final Position[] positions = {new Position(1)};
    final Position[] prevPositions = {};
    test(positions, prevPositions);
  }

  public static void test2() {
    final Position[] positions = {};
    final Position[] prevPositions = {new Position(1)};
    test(positions, prevPositions);
  }

  public static void test3() {
    final Position[] positions = {new Position(1)};
    final Position[] prevPositions = {new Position(1)};
    test(positions, prevPositions);
  }

  public static void test4() {
    final Position[] positions = {new Position(1), new Position(2)};
    final Position[] prevPositions = {new Position(1)};
    test(positions, prevPositions);
  }

  public static void test5() {
    final Position[] positions = {new Position(1)};
    final Position[] prevPositions = {new Position(1), new Position(2)};
    test(positions, prevPositions);
  }

  public static void test6() {
    final Position[] positions = {new Position(1), new Position(3)};
    final Position[] prevPositions = {new Position(1), new Position(2), new Position(3)};
    test(positions, prevPositions);
  }

  public static void test7() {
    final Position[] positions = {new Position(1), new Position(4)};
    final Position[] prevPositions = {new Position(1), new Position(2), new Position(3), new Position(4)};
    test(positions, prevPositions);
  }

  public static void main(String[] args) {
    int i = 23;
    int j = ++i;

    try {
      test7();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
