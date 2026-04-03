package rnd;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.TimeZone;

public class OptionsGenerator {
  public static final long MILLIS_PER_DAY     = 24L * 60 * 60 * 1000;
  public static final long MILLIS_PER_WEEK    = 7L  * MILLIS_PER_DAY;
  public static final long MILLIS_PER_MONTH   = 30L * MILLIS_PER_DAY;
  public static final long MILLIS_PER_QUARTER = 3L  * MILLIS_PER_MONTH;

  public static void main(String[] args) throws IOException {
    final String[][] dates = {
        {"2026-Apr-2", "DAY"},
        {"2026-Apr-3", "WEEK"},
        {"2026-Apr-4", "DAY"},
        {"2026-Apr-5", "DAY"},
        {"2026-Apr-10", "WEEK"},
        {"2026-Apr-17", "WEEK"},
        {"2026-Apr-24", "MONTH"},
        {"2026-May-29", "MONTH"},
        {"2026-Jun-26", "QUARTER"},
        {"2026-Sep-25", "QUARTER"},
        {"2026-Dec-25", "QUARTER"},
        {"2027-Mar-27", "QUARTER"},
    };
    final int[] strikes = {
        50_000,

        54_000,
        56_000,
        58_000,

        60_000,
        61_000,
        62_000,
        63_000,
        64_000,
        65_000,

        65_500,
        66_000,
        66_500,
        67_000,
        67_500,

        68_000, -//

        68_500,
        69_000,
        69_500,
        70_000,
        71_500,

        71_000,
        72_000,
        73_000,
        74_000,
        75_000,
        76_000,

        78_000,
        80_000,
        82_000,

        85_000,
        90_000,
        95_000,
        100_000
    };
    final String file = "./snap/tmp.json";
    Queue<SecurityDefinitionAdminMessageDto> messages = fromJsonFile(file);
    List<SecurityDefinitionAdminMessageDto> output = new ArrayList<>();
    int symbolCount = 0;
    for (String[] entry: dates) {
      symbolCount++;
      long expiryTime = toMillis(entry[0]);
      long rollingTime = getExpireRollTimeMillis(entry[1]);

      String[] dateStrArray = entry[0].split("-");
      //BTC/USD[C]Apr2_82000
      String callSymbol = "BTC/USD[C]" + dateStrArray[1] + dateStrArray[2];
      String callSymbolName = "BTC/USD[Call]" + dateStrArray[1] + dateStrArray[2];
      String putSymbol = "BTC/USD[P]" + dateStrArray[1] + dateStrArray[2];
      String putSymbolName = "BTC/USD[Put]" + dateStrArray[1] + dateStrArray[2];
      for (int strike : strikes) {
        if (symbolCount < 6 && strike > 95_000) {
          continue;
        }
        SecurityDefinitionAdminMessageDto dto = messages.poll();
        if (dto == null) {
          System.out.println("Insufficient number of assets: ");
          return;
        }
        dto.setSymbol(callSymbol + "_" + strike);
        dto.setName(callSymbolName + "_" + strike);
        dto.setExpireTimeMillis(expiryTime);
        dto.setExpireRollTimeMillis(rollingTime);
        dto.setStrikePrice(strike);

        output.add(dto);

        dto = messages.poll();
        if (dto == null) {
          System.out.println("Insufficient number of assets: ");
          return;
        }
        dto.setSymbol(putSymbol + "_" + strike);
        dto.setName(putSymbolName + "_" + strike);
        dto.setExpireTimeMillis(expiryTime);
        dto.setExpireRollTimeMillis(rollingTime);
        dto.setStrikePrice(strike);

        output.add(dto);
      }
    }

    for (SecurityDefinitionAdminMessageDto dto : output) {
      System.out.println(dto.toJson());
    }
    System.out.println();
    while (!messages.isEmpty()) {
      SecurityDefinitionAdminMessageDto dto = messages.poll();
/*      dto.setSymbol("BTC/USD");
      dto.setName(callSymbolName + "_" + strike);
      dto.setExpireTimeMillis(0);
      dto.setExpireRollTimeMillis(0);
      dto.setStrikePrice(0);*/
      System.out.println(dto.toJson());
    }

    System.out.println("Done");
  }

  private static Queue<SecurityDefinitionAdminMessageDto> fromJsonFile(String filePath) throws IOException {
    Queue<SecurityDefinitionAdminMessageDto> messages = new LinkedList<>();
    ObjectMapper mapper = new ObjectMapper();

    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty()) {
          messages.add(mapper.readValue(line, SecurityDefinitionAdminMessageDto.class));
        }
      }
    }

    return messages;
  }

  private static long toMillis(String dateString) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MMM-d", Locale.ENGLISH);
    return LocalDate.parse(dateString, formatter)
        .atTime(LocalTime.MAX)
        .atZone(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli();
  }

  private static long getExpireRollTimeMillis(final String key) {
    if ("DAY".equalsIgnoreCase(key)) {
      return MILLIS_PER_DAY;
    } else if ("WEEK".equalsIgnoreCase(key)) {
      return MILLIS_PER_WEEK;
    } else if ("MONTH".equalsIgnoreCase(key)) {
      return MILLIS_PER_MONTH;
    } else if ("QUARTER".equalsIgnoreCase(key)) {
      return MILLIS_PER_QUARTER;
    }
    return 0;
  }

  private static Queue<SecurityDefinitionAdminMessageDto> reverse(Queue<SecurityDefinitionAdminMessageDto> messages) {
    Deque<SecurityDefinitionAdminMessageDto> stack = new ArrayDeque<>();

    while (!messages.isEmpty()) {
      stack.push(messages.poll());
    }

    return new LinkedList<>(stack);
  }
}
