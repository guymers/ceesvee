package ceesvee.benchmark.data;

import com.univocity.parsers.annotations.NullString;
import com.univocity.parsers.annotations.Parsed;

public class TestDecodeJavaIndex {

  @Parsed(index = 0)
  private String str;

  @NullString(nulls = { "" })
  @Parsed(index = 1)
  private String optStr;

  @Parsed(index = 2)
  private int integer;

  @Parsed(index = 3)
  private float floater;

  @Parsed(index = 4)
  private boolean bool;

  @NullString(nulls = { "" })
  @Parsed(index = 5)
  private Integer optInt;
}
