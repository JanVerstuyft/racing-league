package be.jabapage.racingleague.f1telemetry.util;

import java.util.HashMap;
import java.util.Map;

public class CountryProvider {
    public static class CountryInfo {
        private final String name;
        private final String flagEmoji;

        public CountryInfo(String name, String flagEmoji) {
            this.name = name;
            this.flagEmoji = flagEmoji;
        }

        public String getName() {
            return name;
        }

        public String getFlagEmoji() {
            return flagEmoji;
        }
    }

    private static final Map<Integer, CountryInfo> MAPPING = new HashMap<>();

    static {
        MAPPING.put(1, new CountryInfo("American", "🇺🇸"));
        MAPPING.put(2, new CountryInfo("Argentinean", "🇦🇷"));
        MAPPING.put(3, new CountryInfo("Australian", "🇦🇺"));
        MAPPING.put(4, new CountryInfo("Austrian", "🇦🇹"));
        MAPPING.put(5, new CountryInfo("Azerbaijani", "🇦🇿"));
        MAPPING.put(6, new CountryInfo("Bahraini", "🇧🇭"));
        MAPPING.put(7, new CountryInfo("Belgian", "🇧🇪"));
        MAPPING.put(8, new CountryInfo("Bolivian", "🇧🇴"));
        MAPPING.put(9, new CountryInfo("Brazilian", "🇧🇷"));
        MAPPING.put(10, new CountryInfo("British", "🇬🇧"));
        MAPPING.put(11, new CountryInfo("Bulgarian", "🇧🇬"));
        MAPPING.put(12, new CountryInfo("Cameroonian", "🇨🇲"));
        MAPPING.put(13, new CountryInfo("Canadian", "🇨🇦"));
        MAPPING.put(14, new CountryInfo("Chilean", "🇨🇱"));
        MAPPING.put(15, new CountryInfo("Chinese", "🇨🇳"));
        MAPPING.put(16, new CountryInfo("Colombian", "🇨🇴"));
        MAPPING.put(17, new CountryInfo("Costa Rican", "🇨🇷"));
        MAPPING.put(18, new CountryInfo("Croatian", "🇭🇷"));
        MAPPING.put(19, new CountryInfo("Cypriot", "🇨🇾"));
        MAPPING.put(20, new CountryInfo("Czech", "🇨🇿"));
        MAPPING.put(21, new CountryInfo("Danish", "🇩🇰"));
        MAPPING.put(22, new CountryInfo("Dutch", "🇳🇱"));
        MAPPING.put(23, new CountryInfo("Ecuadorian", "🇪🇨"));
        MAPPING.put(24, new CountryInfo("English", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"));
        MAPPING.put(25, new CountryInfo("Emirian", "🇦🇪"));
        MAPPING.put(26, new CountryInfo("Estonian", "🇪🇪"));
        MAPPING.put(27, new CountryInfo("Finnish", "🇫🇮"));
        MAPPING.put(28, new CountryInfo("French", "🇫🇷"));
        MAPPING.put(29, new CountryInfo("German", "🇩🇪"));
        MAPPING.put(30, new CountryInfo("Ghanaian", "🇬🇭"));
        MAPPING.put(31, new CountryInfo("Greek", "🇬🇷"));
        MAPPING.put(32, new CountryInfo("Guatemalan", "🇬🇹"));
        MAPPING.put(33, new CountryInfo("Honduran", "🇭🇳"));
        MAPPING.put(34, new CountryInfo("Hong Konger", "🇭🇰"));
        MAPPING.put(35, new CountryInfo("Hungarian", "🇭🇺"));
        MAPPING.put(36, new CountryInfo("Icelander", "🇮🇸"));
        MAPPING.put(37, new CountryInfo("Indian", "🇮🇳"));
        MAPPING.put(38, new CountryInfo("Indonesian", "🇮🇩"));
        MAPPING.put(39, new CountryInfo("Irish", "🇮🇪"));
        MAPPING.put(40, new CountryInfo("Israeli", "🇮🇱"));
        MAPPING.put(41, new CountryInfo("Italian", "🇮🇹"));
        MAPPING.put(42, new CountryInfo("Jamaican", "🇯🇲"));
        MAPPING.put(43, new CountryInfo("Japanese", "🇯🇵"));
        MAPPING.put(44, new CountryInfo("Jordanian", "🇯🇴"));
        MAPPING.put(45, new CountryInfo("Kuwaiti", "🇰🇼"));
        MAPPING.put(46, new CountryInfo("Latvian", "🇱🇻"));
        MAPPING.put(47, new CountryInfo("Lebanese", "🇱🇧"));
        MAPPING.put(48, new CountryInfo("Lithuanian", "🇱🇹"));
        MAPPING.put(49, new CountryInfo("Luxembourger", "🇱🇺"));
        MAPPING.put(50, new CountryInfo("Malaysian", "🇲🇾"));
        MAPPING.put(51, new CountryInfo("Maltese", "🇲🇹"));
        MAPPING.put(52, new CountryInfo("Mexican", "🇲🇽"));
        MAPPING.put(53, new CountryInfo("Monegasque", "🇲🇨"));
        MAPPING.put(54, new CountryInfo("New Zealander", "🇳🇿"));
        MAPPING.put(55, new CountryInfo("Nicaraguan", "🇳🇮"));
        MAPPING.put(56, new CountryInfo("Northern Irish", "🏴󠁧󠁢󠁮🇮🇷"));
        MAPPING.put(57, new CountryInfo("Norwegian", "🇳🇴"));
        MAPPING.put(58, new CountryInfo("Omani", "🇴🇲"));
        MAPPING.put(59, new CountryInfo("Pakistani", "🇵🇰"));
        MAPPING.put(60, new CountryInfo("Panamanian", "🇵🇦"));
        MAPPING.put(61, new CountryInfo("Paraguayan", "🇵🇾"));
        MAPPING.put(62, new CountryInfo("Peruvian", "🇵🇪"));
        MAPPING.put(63, new CountryInfo("Polish", "🇵🇱"));
        MAPPING.put(64, new CountryInfo("Portuguese", "🇵🇹"));
        MAPPING.put(65, new CountryInfo("Qatari", "🇶🇦"));
        MAPPING.put(66, new CountryInfo("Romanian", "🇷🇴"));
        MAPPING.put(68, new CountryInfo("Salvadoran", "🇸🇻"));
        MAPPING.put(69, new CountryInfo("Saudi", "🇸🇦"));
        MAPPING.put(70, new CountryInfo("Scottish", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"));
        MAPPING.put(71, new CountryInfo("Serbian", "🇷🇸"));
        MAPPING.put(72, new CountryInfo("Singaporean", "🇸🇬"));
        MAPPING.put(73, new CountryInfo("Slovakian", "🇸🇰"));
        MAPPING.put(74, new CountryInfo("Slovenian", "🇸🇮"));
        MAPPING.put(75, new CountryInfo("South Korean", "🇰🇷"));
        MAPPING.put(76, new CountryInfo("South African", "🇿🇦"));
        MAPPING.put(77, new CountryInfo("Spanish", "🇪🇸"));
        MAPPING.put(78, new CountryInfo("Swedish", "🇸🇪"));
        MAPPING.put(79, new CountryInfo("Swiss", "🇨🇭"));
        MAPPING.put(80, new CountryInfo("Thai", "🇹🇭"));
        MAPPING.put(81, new CountryInfo("Turkish", "🇹🇷"));
        MAPPING.put(82, new CountryInfo("Uruguayan", "🇺🇾"));
        MAPPING.put(83, new CountryInfo("Ukrainian", "🇺🇦"));
        MAPPING.put(84, new CountryInfo("Venezuelan", "🇻🇪"));
        MAPPING.put(85, new CountryInfo("Barbadian", "🇧🇧"));
        MAPPING.put(86, new CountryInfo("Welsh", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"));
        MAPPING.put(87, new CountryInfo("Vietnamese", "🇻🇳"));
        MAPPING.put(88, new CountryInfo("Algerian", "🇩🇿"));
        MAPPING.put(89, new CountryInfo("Bosnian", "🇧🇦"));
        MAPPING.put(90, new CountryInfo("Filipino", "🇵🇭"));
    }

    public static CountryInfo getCountryInfo(Integer nationalityId) {
        if (nationalityId == null) {
            return new CountryInfo("Unknown", "❓");
        }
        return MAPPING.getOrDefault(nationalityId, new CountryInfo("Unknown", "❓"));
    }

    public static String getFlagByName(String name) {
        if (name == null || name.equalsIgnoreCase("Unknown")) {
            return "❓";
        }
        for (CountryInfo info : MAPPING.values()) {
            if (info.getName().equalsIgnoreCase(name)) {
                return info.getFlagEmoji();
            }
        }
        return "❓";
    }

    public static java.util.List<String> getCountryNames() {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (CountryInfo info : MAPPING.values()) {
            if (!list.contains(info.getName())) {
                list.add(info.getName());
            }
        }
        java.util.Collections.sort(list);
        return list;
    }
}
