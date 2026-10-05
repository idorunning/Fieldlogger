package com.field.logger.nativeapp;

import java.time.*;
import java.time.format.TextStyle;
import java.util.*;

/** Local capture dates determine calendar badges. Earned badges never depend on active photos. */
public final class Achievements {
  public static final String[] LEVELS = {"Soil", "Clay", "Flint", "Quartz", "Amber", "Gold"};
  public static final int[] THRESHOLDS = {0, 12, 30, 60, 100, 150};

  /** Material names and colour are display-only: historical badge IDs and unlocks stay intact. */
  public static final int[] LEVEL_COLOURS = {
    0xff76543e, 0xffa94f3a, 0xff4f6576, 0xff76559a, 0xff965408, 0xffcfa830
  };

  public static final int[] LEVEL_BACKGROUND = {
    0xfff0e6de, 0xffffe9df, 0xffe6edf2, 0xffeee5f7, 0xffffedcf, 0xfffff2c7
  };
  public static final String[] LEVEL_DESCRIPTION = {
    "Every trail begins with the soil beneath your feet.",
    "Earth shaped by water: look for colour, texture and tiny details.",
    "A quiet stone with a sharp story, revealed by closer looking.",
    "A glint in the rock: more kinds of discoveries, in different light.",
    "A rare fragment of ancient sunlight, keeping a moment forever.",
    "The rarest trail treasure: a rich collection of lasting memories."
  };

  public static int colour(int layer) {
    return LEVEL_COLOURS[Math.max(0, Math.min(5, layer))];
  }

  public static int background(int layer) {
    return LEVEL_BACKGROUND[Math.max(0, Math.min(5, layer))];
  }

  /** Material-coloured normal text on paper: gold uses a darker ochre for legibility. */
  public static int ink(int layer) {
    return layer >= 5 ? 0xff76601a : colour(layer);
  }

  /** White on the five dark materials, dark brown on bright gold (WCAG AA contrast). */
  public static int onColour(int layer) {
    return layer >= 5 ? 0xff3d330b : 0xffffffff;
  }

  public static final class Badge {
    public final String id, name, description;
    public final int layer, goal, progress;

    Badge(String id, String name, String description, int layer, int goal, int progress) {
      this.id = id;
      this.name = name;
      this.description = description;
      this.layer = layer;
      this.goal = goal;
      this.progress = Math.min(goal, progress);
    }

    public boolean complete() {
      return progress >= goal;
    }
  }

  public static int level(int earned) {
    int level = 0;
    for (int i = 1; i < THRESHOLDS.length; i++) if (earned >= THRESHOLDS[i]) level = i;
    return level;
  }

  private static void add(
      List<Badge> b,
      String id,
      String name,
      String description,
      int layer,
      int goal,
      int progress) {
    b.add(new Badge(id, name, description, layer, goal, progress));
  }

  public static List<Badge> evaluate(List<Observation> records) {
    List<Badge> out = new ArrayList<>();
    Set<String> species = new HashSet<>(),
        areas = new HashSet<>(),
        days = new HashSet<>(),
        months = new HashSet<>();
    Set<Integer> hours = new HashSet<>(), week = new HashSet<>(), monthNumbers = new HashSet<>();
    Map<String, Integer> counts = new HashMap<>(), names = new HashMap<>(), dates = new HashMap<>();
    Map<String, Set<String>> categorySpecies = new HashMap<>();
    int gps = 0, named = 0, early = 0, dawn = 0, evening = 0, night = 0, weekends = 0, weekdays = 0;
    for (Observation r : records) {
      counts.merge(r.category(), 1, Integer::sum);
      if (Observation.knownSpecies(r)) {
        String s = r.data.optString("scientificName").toLowerCase(Locale.ROOT);
        species.add(s);
        categorySpecies.computeIfAbsent(r.category(), k -> new HashSet<>()).add(s);
      }
      if (r.hasGps()) {
        gps++;
        areas.add(
            String.format(
                Locale.US,
                "%.2f,%.2f",
                r.data.optDouble("latitude"),
                r.data.optDouble("longitude")));
      }
      if (!r.place().trim().isEmpty()) named++;
      String identity =
          (r.name() + " " + r.data.optString("scientificName")).toLowerCase(Locale.ROOT);
      names.merge(identity, 1, Integer::sum);
      try {
        LocalDate d = LocalDate.parse(r.day());
        days.add(d.toString());
        months.add(d.toString().substring(0, 7));
        week.add(d.getDayOfWeek().getValue());
        monthNumbers.add(d.getMonthValue());
        dates.merge("month-" + d.getMonthValue(), 1, Integer::sum);
        dates.merge("weekday-" + d.getDayOfWeek().getValue(), 1, Integer::sum);
        dates.merge(
            String.format(Locale.US, "%02d-%02d", d.getMonthValue(), d.getDayOfMonth()),
            1,
            Integer::sum);
        if (d.equals(easter(d.getYear()))) dates.merge("easter", 1, Integer::sum);
        if (d.getDayOfWeek().getValue() >= 6) weekends++;
        else weekdays++;
        int h = r.data.optInt("localHour", -1);
        if (h >= 0 && h < 24) {
          hours.add(h);
          if (h < 9) early++;
          if (h >= 5 && h < 8) dawn++;
          if (h >= 17 && h < 21) evening++;
          if (h >= 21 || h < 5) night++;
          dates.merge("hour-" + h, 1, Integer::sum);
        }
      } catch (Exception ignored) {
      }
    }
    int[] goals = {1, 5, 10, 25, 50, 100, 200, 350, 500, 750, 1000};
    for (int i = 0; i < goals.length; i++)
      add(
          out,
          "photos-" + goals[i],
          i == 0 ? "First wonder" : "A scrapbook of " + goals[i],
          "Save " + goals[i] + " discoveries.",
          Math.min(5, i / 2),
          goals[i],
          records.size());
    int[] collections = {3, 5, 10, 20, 35, 50, 75, 100};
    for (int i = 0; i < collections.length; i++)
      add(
          out,
          "species-" + collections[i],
          "Meet " + collections[i] + " species",
          "Collect " + collections[i] + " different identified species.",
          Math.min(5, i),
          collections[i],
          species.size());
    for (String cat : Observation.CATEGORIES)
      for (int i = 0; i < 6; i++) {
        int goal = new int[] {1, 5, 15, 30, 60, 100}[i];
        add(
            out,
            cat + "-" + goal,
            cat.substring(0, 1).toUpperCase(Locale.UK) + cat.substring(1) + " · " + goal,
            "Photograph " + goal + " discoveries in " + cat + ".",
            i,
            goal,
            counts.getOrDefault(cat, 0));
        if (!cat.equals("other") && !cat.equals("landmarks")) {
          int sg = new int[] {1, 3, 5, 10, 20, 30}[i];
          add(
              out,
              cat + "-species-" + sg,
              "Different " + cat + " · " + sg,
              "Meet " + sg + " distinct identified species of " + cat + ".",
              i,
              sg,
              categorySpecies.getOrDefault(cat, Collections.emptySet()).size());
        }
      }
    for (int i = 0; i < 6; i++) {
      int goal = new int[] {1, 3, 7, 15, 30, 60}[i];
      add(
          out,
          "areas-" + goal,
          "New corners · " + goal,
          "Make discoveries in " + goal + " different map areas (about 1 km across).",
          i,
          goal,
          areas.size());
      add(
          out,
          "days-" + goal,
          "Days worth keeping · " + goal,
          "Photograph something on " + goal + " different days. They need not be consecutive.",
          i,
          goal,
          days.size());
      int mg = new int[] {1, 2, 4, 6, 12, 24}[i];
      add(
          out,
          "months-" + mg,
          "A journal through time · " + mg,
          "Make discoveries in " + mg + " different calendar months.",
          i,
          mg,
          months.size());
    }
    add(
        out,
        "all-kinds",
        "A little of everything",
        "Notice all eight categories of discoveries.",
        2,
        8,
        counts.size());
    add(
        out,
        "all-week",
        "Every day has a story",
        "Photograph something on each day of the week.",
        2,
        7,
        week.size());
    add(
        out,
        "all-months",
        "A whole year of wonder",
        "Make a discovery in every month of the year.",
        4,
        12,
        monthNumbers.size());
    add(
        out,
        "hours-8",
        "Different light",
        "Collect discoveries in eight different hours of the day.",
        3,
        8,
        hours.size());
    add(
        out,
        "hours-16",
        "Round the clock",
        "Collect discoveries in sixteen different hours. No need to stay up late.",
        5,
        16,
        hours.size());
    String[] timeNames = {
      "Early bird",
      "First light",
      "Evening glow",
      "After dark",
      "Weekend wonders",
      "An ordinary day, noticed",
      "On the map",
      "A sense of place"
    };
    String[] timeDesc = {
      "before 9 am",
      "between 5 and 8 am",
      "between 5 and 9 pm",
      "between 9 pm and 5 am",
      "at the weekend",
      "on weekdays",
      "with saved coordinates",
      "with a place name"
    };
    int[] values = {early, dawn, evening, night, weekends, weekdays, gps, named};
    for (int t = 0; t < values.length; t++)
      for (int i = 0; i < 3; i++) {
        int goal = new int[] {1, 5, 20}[i];
        add(
            out,
            "time-" + t + "-" + goal,
            timeNames[t] + (i == 0 ? "" : " · " + goal),
            "Save " + goal + " discoveries " + timeDesc[t] + ".",
            Math.min(5, i + t % 2),
            goal,
            values[t]);
      }
    for (int m = 1; m <= 12; m++)
      for (int i = 0; i < 2; i++) {
        String label = Month.of(m).getDisplayName(TextStyle.FULL, Locale.UK);
        int goal = i == 0 ? 1 : 10;
        add(
            out,
            "month-" + m + "-" + goal,
            label + " memories" + (i == 0 ? "" : " · 10"),
            "Save " + goal + " discoveries photographed in " + label + ".",
            i == 0 ? 1 : 3,
            goal,
            dates.getOrDefault("month-" + m, 0));
      }
    for (int d = 1; d <= 7; d++) {
      String label = DayOfWeek.of(d).getDisplayName(TextStyle.FULL, Locale.UK);
      add(
          out,
          "weekday-" + d,
          label + " pause",
          "Save five discoveries photographed on a " + label + ".",
          2,
          5,
          dates.getOrDefault("weekday-" + d, 0));
    }
    String[][] holidays = {
      {"01-01", "A fresh beginning", "New Year’s Day"},
      {"01-25", "Burns Night wander", "25 January"},
      {"02-02", "Winter turning", "2 February"},
      {"02-14", "Love your outdoors", "Valentine’s Day"},
      {"02-29", "One in four", "Leap Day, 29 February"},
      {"03-01", "Daffodil day", "St David’s Day"},
      {"03-17", "A little green", "St Patrick’s Day"},
      {"03-20", "Spring’s doorstep", "20 March"},
      {"04-01", "No fooling nature", "1 April"},
      {"04-22", "Our remarkable Earth", "Earth Day, 22 April"},
      {"04-23", "English countryside", "St George’s Day"},
      {"05-01", "May Day moment", "1 May"},
      {"05-20", "Busy little pollinators", "World Bee Day, 20 May"},
      {"05-22", "Life in all its forms", "Biodiversity Day, 22 May"},
      {"06-05", "A greener thought", "Environment Day, 5 June"},
      {"06-08", "Blue planet", "World Oceans Day, 8 June"},
      {"06-21", "Long light", "21 June"},
      {"07-01", "High summer", "1 July"},
      {"08-01", "August abundance", "1 August"},
      {"09-22", "Autumn’s doorstep", "22 September"},
      {"10-04", "Animal appreciation", "World Animal Day, 4 October"},
      {"10-31", "Spooky little wonders", "Halloween"},
      {"11-05", "November glow", "5 November"},
      {"11-11", "A quiet remembrance", "11 November"},
      {"11-30", "St Andrew’s discovery", "St Andrew’s Day"},
      {"12-21", "Winter light", "21 December"},
      {"12-24", "A Christmas Eve pause", "Christmas Eve"},
      {"12-25", "Christmas Day wonder", "Christmas Day"},
      {"12-26", "Boxing Day fresh air", "Boxing Day"},
      {"12-31", "The year’s last page", "New Year’s Eve"},
      {"easter", "An Easter discovery", "Easter Sunday"}
    };
    for (String[] h : holidays)
      add(
          out,
          "calendar-" + h[0],
          h[1],
          "Photograph a discovery on " + h[2] + ". Earn it once and keep it forever.",
          1,
          1,
          dates.getOrDefault(h[0], 0));
    String[][] subjects = {
      {"oak", "Oak acquaintance"},
      {"beech", "Beech beauty"},
      {"pine", "Pine scent"},
      {"birch", "Silver storyteller"},
      {"willow", "Willow whisper"},
      {"ash", "Ash encounter"},
      {"holly", "Winter evergreen"},
      {"hazel", "Hazel haven"},
      {"sycamore", "Winged seeds"},
      {"yew", "Old woodland guardian"},
      {"rowan", "Rowan visitor"},
      {"chestnut", "Chestnut curiosity"},
      {"maple", "Maple moment"},
      {"alder", "Waterside alder"},
      {"daisy", "Daisy delight"},
      {"dandelion", "Everyday sunshine"},
      {"bluebell", "A bluebell moment"},
      {"buttercup", "Golden little cup"},
      {"foxglove", "Foxglove bells"},
      {"poppy", "A poppy pause"},
      {"snowdrop", "Winter’s white promise"},
      {"daffodil", "Spring trumpet"},
      {"primrose", "Primrose promise"},
      {"clover", "Clover close-up"},
      {"lavender", "Lavender lull"},
      {"heather", "Heather hillside"},
      {"orchid", "An orchid encounter"},
      {"rose", "Rose discovery"},
      {"nettle", "Look again at nettles"},
      {"fern", "Fern unfurling"},
      {"moss", "A tiny green forest"},
      {"ivy", "Ivy invitation"},
      {"robin", "Robin company"},
      {"blackbird", "Blackbird hello"},
      {"sparrow", "Sparrow spotter"},
      {"blue tit", "Blue tit glimpse"},
      {"great tit", "Great tit moment"},
      {"wren", "Little wren"},
      {"woodpecker", "Woodland drummer"},
      {"kingfisher", "A flash of blue"},
      {"heron", "Patient waterside watcher"},
      {"owl", "Owl encounter"},
      {"duck", "Duckside diary"},
      {"swan", "Swan serenity"},
      {"buzzard", "Buzzard above"},
      {"pigeon", "Pigeon perspective"},
      {"butterfly", "Butterfly pause"},
      {"bee", "Bee busy"},
      {"ladybird", "Spotted little visitor"},
      {"dragonfly", "Dragonfly shimmer"},
      {"beetle", "Beetle beginnings"},
      {"moth", "Moth moment"},
      {"spider", "Silken world"},
      {"snail", "Slow and lovely"},
      {"ant", "Ant’s eye view"},
      {"grasshopper", "Meadow musician"},
      {"fox", "Fox encounter"},
      {"squirrel", "Squirrel surprise"},
      {"deer", "Deer diary"},
      {"rabbit", "Rabbit ramble"},
      {"hedgehog", "Prickly little neighbour"},
      {"badger", "Badger moment"},
      {"hare", "Hare encounter"},
      {"otter", "Otter occasion"},
      {"mushroom", "Mushroom mystery"},
      {"lichen", "Lichen, looked at"},
      {"bridge", "Bridge story"},
      {"river", "River reflection"},
      {"waterfall", "Waterfall wonder"},
      {"castle", "Castle curiosity"}
    };
    for (int s = 0; s < subjects.length; s++) {
      String key = subjects[s][0];
      int n = 0;
      for (Map.Entry<String, Integer> e : names.entrySet())
        if (e.getKey().matches(".*\\b" + java.util.regex.Pattern.quote(key) + "\\b.*"))
          n += e.getValue();
      add(
          out,
          "subject-" + key,
          subjects[s][1],
          "Photograph a discovery identified as " + key + ".",
          2 + s % 4,
          1,
          n);
      add(
          out,
          "subject-" + key + "-5",
          "Getting to know " + key,
          "Photograph " + key + " on five occasions.",
          4 + s % 2,
          5,
          n);
    }
    return out;
  }

  private static LocalDate easter(int year) {
    int a = year % 19,
        b = year / 100,
        c = year % 100,
        d = b / 4,
        e = b % 4,
        f = (b + 8) / 25,
        g = (b - f + 1) / 3,
        h = (19 * a + b - d - g + 15) % 30,
        i = c / 4,
        k = c % 4,
        l = (32 + 2 * e + 2 * i - h - k) % 7,
        m = (a + 11 * h + 22 * l) / 451;
    int n = h + l - 7 * m + 114;
    return LocalDate.of(year, n / 31, n % 31 + 1);
  }
}
