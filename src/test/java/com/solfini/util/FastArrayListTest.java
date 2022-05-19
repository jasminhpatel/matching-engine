package com.solfini.util;

import java.util.List;
import com.solfini.util.FastArrayList;
import org.junit.Assert;
import org.junit.Test;

class Element {
  private final String value;
  public Element(final String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }
}

public class FastArrayListTest {

  @Test
	public void toStringWithNullElements() {
    List<Element> list = new FastArrayList<Element>();
    list.add(new Element("foo"));
    list.add(null);
    list.add(new Element("bar"));

    try {
      Assert.assertEquals(new String("[foo, null, bar]"), list.toString());
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }
}
