package until.the.eternity.das.user.entity.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NicknameAdjective {
  STRONG("강인한"), LAZY("게으른"), NOBLE("고결한"), ANCIENT("고대의"), BERSERK("광폭한"),
  ANNOYING("귀찮은"), SHADOW("그림자의"), LOST("길잃은"), COLD("냉혹한"), RELAXED("느긋한"),
  CLUMSY("덤벙대는"), WARM("따뜻한"), FIERCE("맹렬한"), BLANK("멍한"), HONORABLE("명예로운"),
  RUTHLESS("무자비한"), HUNGRY("배고픈"), LIGHTNING("번개의"), DILIGENT("부지런한"), FURIOUS("분노한"),
  INDOMITABLE("불굴의"), SECRETIVE("비밀스러운"), SHINING("빛나는"), SAVAGE("사나운"), SINCERE("성실한"),
  SIMPLE("소박한"), SUSPICIOUS("수상한"), GUARDIAN("수호하는"), PURE("순수한"), FAITH("신념의"),
  HOLY("신성한"), MISTAKEN("실수하는"), FRESH("싱그러운"), DARK("어둠의"), UNFAIR("억울한"),
  ICE("얼음의"), GLORY("영광의"), BRAVE("용감한"), GREAT("위대한"), SUBTLE("은은한"),
  STRANGE("이상한"), NATURAL("자연스러운"), FREE("자유로운"), CALM("잔잔한"), LEGENDARY("전설적인"),
  SLEEPY("졸린"), LOYAL("충직한"), DEADLY("치명적인"), DOOM("파멸의"), PEACEFUL("평온한"),
  COZY("포근한"), STORM("폭풍의"), LEISURELY("한가로운"), FLAME("화염의");

  private final String value;
  private static final Random RANDOM = new Random();
  private static final List<NicknameAdjective> VALUES = Arrays.asList(values());

  public static NicknameAdjective random() {
    return VALUES.get(RANDOM.nextInt(VALUES.size()));
  }

  public static boolean contains(String value) {
    return Arrays.stream(values()).anyMatch(v -> v.getValue().equals(value));
  }
}
