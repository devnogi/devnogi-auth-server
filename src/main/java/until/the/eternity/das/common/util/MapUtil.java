package until.the.eternity.das.common.util;

public class MapUtil {

  private MapUtil() {
  }

  public static <T> T cast(Object obj, Class<T> clazz) {
    if (clazz.isInstance(obj)) {
      return (T) obj;
    }
    throw new ClassCastException("Cannot cast to " + clazz.getName());
  }
}
