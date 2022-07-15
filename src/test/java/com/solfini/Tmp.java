package com.solfini;

import com.solfini.internal.admin.schema.BalanceAdminMessageDecoder;

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static com.solfini.instrument.Position.assetIdComparator;

public class Tmp {
  public static void main(String[] args) {
    Set<long[]> assetIdtreeSet = new TreeSet<>(assetIdComparator);
    long[] arrvalue = new long[] {107, 6};
    assetIdtreeSet.add(arrvalue);
    arrvalue = new long[] {108, 7};
    assetIdtreeSet.add(arrvalue);
    arrvalue = new long[] {109, 8};
    assetIdtreeSet.add(arrvalue);
    arrvalue = new long[] {110, 9};
    assetIdtreeSet.add(arrvalue);
    arrvalue = new long[] {111, 10};
    assetIdtreeSet.add(arrvalue);

    Set<long[]> assetIdtreeSet2 = new TreeSet<long[]>(assetIdComparator);
    assetIdtreeSet2.addAll(assetIdtreeSet);

    Set<long[]> assetIdtreeSet3 = new TreeSet<long[]>(assetIdtreeSet);

    System.out.println("Done.");
  }

  public static final Comparator<long[]> assetIdComparator = new Comparator<long[]>() {
    @Override
    public int compare(final long[] o1, final long[] o2) {
      if (o1[0] < o2[0])
        return -1;
      if (o1[0] > o2[0])
        return 1;

      if (o1[1] < o2[1])
        return -1;
      if (o1[1] > o2[1])
        return 1;

      return 0;
    }
  };
}
