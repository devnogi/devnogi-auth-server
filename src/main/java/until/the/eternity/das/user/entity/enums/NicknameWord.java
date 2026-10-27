package until.the.eternity.das.user.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Getter
@RequiredArgsConstructor
public enum NicknameWord {
  // Races
  HUMAN("인간"), ELF("요정"), GIANT("자이언트"),

  // Jobs
  WARRIOR("전사"), WIZARD("마법사"), ARCHER("궁수"), BATTLE_ALCHEMIST("전투연금술사"),
  FIGHTER("격투가"), BARD("음유시인"), PUPPETEER("인형사"), LANCER("랜서"),
  ALCHEMY_MASTER("연성연금술사"), SHOOTER("슈터"), NINJA("닌자"), CHAIN_SLASHER("체인슬래셔"),
  BATTLE_ASTROLOGER("전투점성술사"), ADVENTURER("모험가"), MERCHANT("상인"), PRIEST("사제"),
  CHEF("요리사"), BLACKSMITH("대장장이"), TAILOR("재단사"), PHARMACIST("약사"),
  CARPENTER("목수"), PET_HANDLER("펫핸들러"), MAGIGRAPHER("마기그래퍼"), DESTINY_ASTROLOGER("운명점성술사"),
  MERLIN("멀린"), DIVA("디바"), PROFESSOR_J("프로페서J"), MASTER_CHEF("마스터셰프"),
  TREASURE_HUNTER("트레저헌터");

  private final String value;
  private static final Random RANDOM = new Random();
  private static final List<NicknameWord> VALUES = Arrays.asList(values());

  public static NicknameWord random() {
    return VALUES.get(RANDOM.nextInt(VALUES.size()));
  }

  public static boolean contains(String value) {
    return Arrays.stream(values())
      .anyMatch(v -> v.getValue()
        .equals(value));
  }
}
