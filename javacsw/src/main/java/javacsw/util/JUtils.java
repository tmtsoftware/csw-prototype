package javacsw.util;

import akka.japi.Pair;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * General purpose Java utility functions
 */
public class JUtils {

  /**
   * Simple Java equivalent of the Scala collections zip function
   */
  public static<A, B> List<Pair<A, B>> zip(List<A> a, List<B> b) {
    return IntStream.range(0, Math.min(a.size(), b.size()))
      .mapToObj(i -> new Pair<>(a.get(i), b.get(i))).collect(Collectors.toList());
  }

}
