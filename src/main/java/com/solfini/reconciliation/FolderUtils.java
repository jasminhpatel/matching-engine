package com.solfini.reconciliation;

import com.solfini.util.StringUtil;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FolderUtils {
  public static List<String> getLastNTimestampFolders(final String snapDirectory, final int count) {
    final File parentFolder = new File(snapDirectory);
    if (!parentFolder.exists() || !parentFolder.isDirectory()) {
      throw new IllegalArgumentException("Invalid folder path: " + parentFolder);
    }

    final File[] subFolders = parentFolder.listFiles(File::isDirectory);
    if (subFolders == null) {
      return Collections.emptyList();
    }

    return Arrays.stream(subFolders)
        .map(File::getName)
        .filter(name -> name.matches("\\d+"))
        .map(StringUtil::toLong)
        .sorted(Comparator.reverseOrder())
        .limit(count)
        .map(String::valueOf)
        .collect(Collectors.toList());
  }
}
