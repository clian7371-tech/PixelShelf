package com.pixelshelf.app;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 封面名匹配：
 * 1) 内置一份中文→英文别名表（覆盖常见汉化游戏）
 * 2) 用 ROM 内部官方标题（GBA/NDS 头部、PSP 的 PARAM.SFO）做词级模糊匹配
 * 3) 最后才退回文件名
 */
public final class CoverMatch {

    /** 中文关键词 → 英文官方名片段，帮助汉化 ROM 命中 */
    private static final String[][] ALIASES = {
            // 宝可梦
            {"心金", "HeartGold"}, {"魂银", "SoulSilver"},
            {"火红", "FireRed"}, {"叶绿", "LeafGreen"},
            {"红宝石", "Ruby"}, {"蓝宝石", "Sapphire"}, {"绿宝石", "Emerald"},
            {"钻石", "Diamond"}, {"珍珠", "Pearl"}, {"白金", "Platinum"},
            {"黑", "Black"}, {"白", "White"},
            {"究极日", "Ultra Sun"}, {"究极月", "Ultra Moon"},
            {"太阳", "Sun"}, {"月亮", "Moon"},
            {"皮卡丘", "Pikachu"}, {"不可思议的迷宫", "Mystery Dungeon"},
            {"空之探险队", "Explorers of Sky"}, {"时之探险队", "Explorers of Time"},
            {"暗之探险队", "Explorers of Darkness"},
            {"护林员", "Ranger"}, {"保育家", "Ranger"},
            // 马里奥
            {"马里奥", "Mario"}, {"超级马里奥", "Super Mario"},
            {"新超级马里奥", "New Super Mario"}, {"纸片马里奥", "Paper Mario"},
            {"马里奥赛车", "Mario Kart"}, {"路易吉", "Luigi"},
            {"耀西", "Yoshi"}, {"瓦里奥", "Wario"},
            {"马力欧", "Mario"}, {"玛利欧", "Mario"},
            // 塞尔达
            {"塞尔达", "Zelda"}, {"传说", "Legend"},
            {"众神的三角力量", "A Link to the Past"},
            {"缩小帽", "The Minish Cap"}, {"梦见岛", "Link's Awakening"},
            {"时之笛", "Ocarina of Time"}, {"幻影沙漏", "Phantom Hourglass"},
            {"大地的汽笛", "Spirit Tracks"},
            // 恶魔城
            {"恶魔城", "Castlevania"}, {"晓月圆舞曲", "Aria of Sorrow"},
            {"白夜协奏曲", "Harmony of Dissonance"}, {"月轮", "Circle of the Moon"},
            {"苍月的十字架", "Dawn of Sorrow"}, {"迷宫的画廊", "Portrait of Ruin"},
            {"被夺的刻印", "Order of Ecclesia"},
            // 最终幻想
            {"最终幻想", "Final Fantasy"}, {"水晶编年史", "Crystal Chronicles"},
            {"战略版", "Tactics"},
            // 勇者斗恶龙
            {"勇者斗恶龙", "Dragon Quest"}, {"怪兽篇", "Monsters"},
            // 火焰纹章
            {"火焰纹章", "Fire Emblem"}, {"圣魔之光石", "The Sacred Stones"},
            {"烈火之剑", "Blazing Blade"}, {"封印之剑", "The Binding Blade"},
            // 战神
            {"战神", "God of War"}, {"奥林匹斯之链", "Chains of Olympus"},
            {"斯巴达之魂", "Ghost of Sparta"},
            // 火影 / 海贼 / 死神
            {"火影忍者", "Naruto"}, {"疾风传", "Shippuden"},
            {"究极冲击", "Ultimate Ninja Impact"},
            {"究极觉醒", "Ultimate Ninja Heroes"}, {"海贼王", "One Piece"},
            {"死神", "Bleach"},
            // 无双
            {"真三国无双", "Dynasty Warriors"}, {"三国无双", "Dynasty Warriors"},
            {"战国无双", "Samurai Warriors"}, {"无双大蛇", "Warriors Orochi"},
            // 三国志 / 信长
            {"三国志", "Romance of the Three Kingdoms"}, {"信长之野望", "Nobunaga"},
            // 怪物猎人
            {"怪物猎人", "Monster Hunter"}, {"携带版", "Portable"},
            // 合金装备
            {"合金装备", "Metal Gear"}, {"和平行者", "Peace Walker"},
            {"掌上行动", "Portable Ops"},
            // 山脊赛车 / 极品飞车
            {"山脊赛车", "Ridge Racer"}, {"极品飞车", "Need for Speed"},
            // 侠盗猎车
            {"侠盗猎车", "Grand Theft Auto"}, {"自由城", "Liberty City"},
            {"罪恶都市", "Vice City"},
            // 王国之心
            {"王国之心", "Kingdom Hearts"}, {"梦中降生", "Birth by Sleep"},
            // 女神异闻录
            {"女神异闻录", "Persona"}, {"真女神转生", "Shin Megami Tensei"},
            // 洛克人
            {"洛克人", "Mega Man"}, {"元祖", "Mega Man"}, {"X", "Mega Man X"},
            {"EXE", "Battle Network"}, {"流星", "Star Force"},
            // 索尼克
            {"索尼克", "Sonic"},
            // 星之卡比
            {"星之卡比", "Kirby"}, {"镜之迷宫", "Amazing Mirror"},
            // 银河战士
            {"银河战士", "Metroid"}, {"融合", "Fusion"}, {"零点任务", "Zero Mission"},
            // 黄金太阳
            {"黄金太阳", "Golden Sun"},
            // 逆转裁判
            {"逆转裁判", "Ace Attorney"}, {"逆转检事", "Investigations"},
            // 牧场物语
            {"牧场物语", "Harvest Moon"}, {"矿石镇", "Friends of Mineral Town"},
            // 太鼓达人
            {"太鼓达人", "Taiko"}, {"太鼓之达人", "Taiko"},
            // 其他常见
            {"俄罗斯方块", "Tetris"}, {"炸弹人", "Bomberman"},
            {"蝙蝠侠", "Batman"}, {"蜘蛛侠", "Spider-Man"},
            {"忍者神龟", "Teenage Mutant Ninja Turtles"},
            {"辛普森", "Simpsons"}, {"哈利波特", "Harry Potter"},
            {"指环王", "Lord of the Rings"}, {"星球大战", "Star Wars"},
            {"乐高", "LEGO"}, {"极品摩托", "MotoGP"},
            {"实况足球", "Pro Evolution Soccer"}, {"胜利十一人", "Winning Eleven"},
            {"FIFA", "FIFA"}, {"NBA", "NBA"}, {"WWE", "WWE"},
            {"铁拳", "Tekken"}, {"灵魂能力", "Soulcalibur"},
            {"街头霸王", "Street Fighter"}, {"拳皇", "King of Fighters"},
            {"VR战士", "Virtua Fighter"}, {"死或生", "Dead or Alive"},
            {"寂静岭", "Silent Hill"}, {"生化危机", "Resident Evil"},
            {"零", "Fatal Frame"}, {"鬼泣", "Devil May Cry"},
            {"但丁地狱", "Dante's Inferno"}, {"刺客信条", "Assassin's Creed"},
            {"波斯王子", "Prince of Persia"}, {"古墓丽影", "Tomb Raider"},
            {"战神金刚", "Voltron"}, {"高达", "Gundam"},
            {"超级机器人大战", "Super Robot Wars"}, {"SD高达", "SD Gundam"},
            {"机战", "Super Robot Wars"}, {"第三次超级机器人大战", "Super Robot Wars"},
    };

    /** 归一化：小写 + 只留字母数字 */
    static String norm(String s) {
        if (s == null) return "";
        String lower = s.toLowerCase(Locale.US);
        StringBuilder sb = new StringBuilder(lower.length());
        int depth = 0;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
                continue;
            }
            if (c == ')' || c == ']') {
                if (depth > 0) depth--;
                continue;
            }
            if (depth > 0) continue;
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /** 把中文名里的关键词换成英文，生成额外的查询串 */
    static List<String> expandAliases(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String hit = "";
        int best = 0;
        for (String[] pair : ALIASES) {
            if (raw.contains(pair[0])) {
                // 选最长的匹配，避免「马里奥」压过「新超级马里奥」
                if (pair[0].length() > best) {
                    best = pair[0].length();
                    hit = pair[1];
                }
            }
        }
        if (!hit.isEmpty()) {
            out.add(hit);
            // 组合多个关键词（两个）
            StringBuilder combo = new StringBuilder();
            List<String> parts = new ArrayList<>();
            String rest = raw;
            for (String[] pair : ALIASES) {
                if (rest.contains(pair[0])) {
                    parts.add(pair[1]);
                    rest = rest.replace(pair[0], " ");
                }
            }
            if (parts.size() >= 2) {
                for (int i = 0; i < Math.min(parts.size(), 3); i++) {
                    if (combo.length() > 0) combo.append(' ');
                    combo.append(parts.get(i));
                }
                out.add(combo.toString());
            }
        }
        return out;
    }

    /** 分词（按空格、连字符、冒号等切） */
    static String[] tokens(String s) {
        String n = norm(s);
        if (n.isEmpty()) return new String[0];
        // 用原串的空格切分更合理
        String[] raw = s.toLowerCase(Locale.US).split("[\\s\\-_:,.!?'’/\\\\]+");
        List<String> out = new ArrayList<>();
        for (String r : raw) {
            String t = norm(r);
            if (t.length() >= 2) out.add(t);
        }
        if (out.isEmpty()) out.add(n);
        return out.toArray(new String[0]);
    }

    /** 计算查询串与候选名的相似度 0..1 */
    static float score(String query, String candidate) {
        String q = norm(query);
        String c = norm(candidate);
        if (q.isEmpty() || c.isEmpty()) return 0f;
        if (q.equals(c)) return 1f;

        // 包含关系
        if (c.contains(q) && q.length() >= 4) {
            return 0.90f + 0.09f * (q.length() / (float) c.length());
        }
        if (q.contains(c) && c.length() >= 6) {
            return 0.80f + 0.15f * (c.length() / (float) q.length());
        }

        // 词级重合
        String[] qt = tokens(query);
        String[] ct = tokens(candidate);
        if (qt.length == 0 || ct.length == 0) return 0f;

        int hit = 0;
        for (String t : qt) {
            for (String u : ct) {
                if (u.equals(t)) {
                    hit += 2;
                    break;
                }
                if (u.startsWith(t) && t.length() >= 3) {
                    hit += 1;
                    break;
                }
                if (t.startsWith(u) && u.length() >= 3) {
                    hit += 1;
                    break;
                }
            }
        }
        float denom = qt.length * 2f;
        return Math.min(0.78f, hit / denom * 0.78f);
    }

    /** 候选名里的区域/版本偏好：优先 USA / Europe / World */
    static int regionBonus(String name) {
        String n = name.toLowerCase(Locale.US);
        if (n.contains("(usa, europe)")) return 6;
        if (n.contains("(world)")) return 5;
        if (n.contains("(usa)")) return 6;
        if (n.contains("(europe)")) return 4;
        if (n.contains("(japan)") || n.contains("(korea)")) return 1;
        if (n.contains("beta") || n.contains("demo") || n.contains("promo")
                || n.contains("proto") || n.contains("sample")) return -8;
        return 3;
    }

    /** 在索引中找最佳候选；返回索引里的原始名 */
    public static String best(Context ctx, String platform, List<String> queries) {
        List<String> names = CoverIndex.names(ctx, platform);
        if (names.isEmpty()) return null;

        String bestName = null;
        float bestScore = 0f;

        for (String q : queries) {
            if (q == null || q.trim().isEmpty()) continue;
            for (int i = 0; i < names.size(); i++) {
                String cand = names.get(i);
                float s = score(q, cand);
                if (s < 0.55f) continue;
                // 区域加分
                s += regionBonus(cand) * 0.01f;
                if (s > bestScore) {
                    bestScore = s;
                    bestName = cand;
                }
            }
            // 已经足够好就不再看后面的查询串
            if (bestScore >= 0.92f) break;
        }
        return bestScore >= 0.55f ? bestName : null;
    }

    private CoverMatch() {
    }
}
