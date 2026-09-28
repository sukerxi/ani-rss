package ani.rss.enums;

public class StringEnum {
    public static final String SEASON_REG = "[Ss](\\d+)[Ee](\\d+(\\.5)?)";
    public static final String YEAR_REG = " ?\\(((19|20)\\d{2})\\)";
    public static final String TMDB_ID_REG = " ?(\\[tmdbid=(\\d+)]|\\{tmdb-(\\d+)})";
    public static final String MAGNET_REG = "^magnet\\:\\?xt=urn:btih\\:(\\w+)";
    /**
     * BitTorrent info hash：SHA-1，40 位十六进制字符（允许大写，使用前通常会转小写）
     */
    public static final String INFO_HASH_REG = "^[a-fA-F0-9]{40}$";
    public static final String SUBGROUP_REG_STR = "^\\{\\{(.+)}}:(.+)$";
    public static final String ED2K_REG = "^ed2k://\\|file\\|([^|]+)\\|(\\d+)\\|([A-Fa-f0-9]{32})\\|/$";
}
