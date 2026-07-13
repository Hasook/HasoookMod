package com.hasoook.hasoook.screen.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hasoook.hasoook.Config;
import com.hasoook.hasoook.network.payload.DrawSaveMapPayload;
import com.hasoook.hasoook.network.payload.GiveResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PaintProcessor {
    private static final int LOCAL_MATCH_TARGET_SIZE = 32;
    private static final double LOCAL_MATCH_MIN_SCORE = 0.72;
    private static final int LOCAL_TOP_CANDIDATES = 8;

    private static final Map<String, BufferedImage> ITEM_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, int[]> TEXTURE_COLOR_SIGNATURE = new ConcurrentHashMap<>();
    private static volatile boolean ITEM_TEXTURES_LOADED = false;
    private static volatile boolean ITEM_TEXTURES_LOADING = false;

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static String getApiUrl() {
        return Config.PAQ_API_URL.get();
    }
    private static String getModelName() {
        return Config.PAQ_MODEL_NAME.get();
    }

    private static final Pattern ID_PATTERN = Pattern.compile("(minecraft:[a-z0-9_]+)");

    // 任务状态
    private static String activeQuestId = null;
    private static String activeQuestName = null;
    private static boolean activeQuestIsEntity = false;

    public static String getActiveQuestName() { return activeQuestName; }

    // 异步识别玩家画作
    public static void recognizeImage(BufferedImage rawImage, int canvasBgColor) {
        CompletableFuture.runAsync(() -> {
            try {
                ensureItemTexturesLoaded();

                // 图像预处理
                BufferedImage cropped = cropTransparentContent(rawImage, 2);
                BufferedImage normalizedPlayerImage = fitToSquare(cropped, LOCAL_MATCH_TARGET_SIZE);
                List<MatchCandidate> localMatches = findBestLocalMatches(normalizedPlayerImage);

                BufferedImage upload = dynamicUpscale(fillTransparentBackground(cropped, canvasBgColor), 256);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(upload, "png", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                // 构建请求
                boolean isQuestMode = (activeQuestId != null);
                String prompt = isQuestMode ? buildQuestPrompt() : getNormalPrompt(buildLocalCandidateHint(localMatches));
                String jsonBody = buildVisionJson(b64, prompt);

                // 发送并解析
                String aiRawOutput = sendApiRequest(jsonBody);
                AiResponse response = parseAiResponse(aiRawOutput);

                // 主线程回调
                executeInMainThread(() -> {
                    String thinkingLog = response.thinking == null ? "AI未提供分析过程" : response.thinking;

                    if (isQuestMode) {
                        String finalLog = thinkingLog + "\n\n==== 最终评价 ====\n" + (response.reason == null ? "无" : response.reason);
                        if (writeThinkingToWritableBook(finalLog)) {
                            msg("§7分析过程与评价已写入你背包中的书与笔。");
                        }

                        if (Boolean.TRUE.equals(response.is_match)) {
                            msg("§a描绘被接受了！颜料成为了§e" + getActiveQuestDisplayName() + "§a！");
                            giveResultToServer(activeQuestId, activeQuestIsEntity);
                            if (Config.PAQ_GIVE_MAP.get()) {
                                sendMapToServer(rawImage, getActiveQuestDisplayName());
                            }
                        } else {
                            msg("§c§l这不是" + getActiveQuestDisplayName() + "！");
                            msg("§d请继续尝试描绘：§e" + getActiveQuestDisplayName());
                        }
                    } else {
                        boolean wrote = writeThinkingToWritableBook(thinkingLog);
                        if (wrote) {
                            msg("§7分析过程已写入你背包中的书与笔。");
                        }

                        FinalResult result = parseAndValidateAiResult(response, localMatches);
                        String displayName = result.isEntity ? getEntityDisplayName(result.id) : getItemDisplayName(result.id);
                        if (Config.PAQ_GIVE_MAP.get()) {
                            sendMapToServer(rawImage, displayName);
                        }
                        msg("§a这看起来像：" + displayName + "！");
                        giveResultToServer(result.id, result.isEntity);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                executeInMainThread(() -> msg("§c识别时发生异常，API请求失败。"));
            }
        });
    }

    // 网络请求与 JSON 构建
    private static String sendApiRequest(String jsonBody) throws Exception {
        String apiKey = Config.PAQ_API_KEY.get();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(getApiUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("API请求异常，状态码：" + resp.statusCode());
        }

        Map<?, ?> responseMap = GSON.fromJson(resp.body(), Map.class);
        List<?> choices = (List<?>) responseMap.get("choices");
        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        return (String) message.get("content");
    }

    private static String buildVisionJson(String base64Image, String textPrompt) {
        Map<String, Object> payload = Map.of(
                "model", getModelName(),
                "max_tokens", 300,
                "temperature", 0.05,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", textPrompt),
                                Map.of("type", "image_url", "image_url", Map.of("url", "data:image/png;base64," + base64Image))
                        )
                ))
        );
        return GSON.toJson(payload);
    }

    private static AiResponse parseAiResponse(String rawContent) {
        if (rawContent == null) return new AiResponse(null, null, null, null, false, null);
        String jsonStr = rawContent.replaceAll("(?is)```json", "").replaceAll("(?is)```", "").trim();
        try {
            return GSON.fromJson(jsonStr, AiResponse.class);
        } catch (Exception e) {
            e.printStackTrace();
            return new AiResponse("解析失败: " + e.getMessage(), null, null, null, false, null);
        }
    }

    // 提示词生成
    private static String buildQuestPrompt() {
        return """
            你是一个专门审核 Minecraft 玩家绘画任务的模型。
            玩家当前接到的强制任务是描绘：【%s】(ID: %s)。
            
            【关键前提】
            - 忽略背景。
            - 玩家画技受限，只要有较多符合特征，请宽容判定通过。
            - 如果完全无关或颜色完全不符，判定不通过。
            
            请严格输出以下格式的 JSON（不要包含 Markdown 代码块）：
            {
              "thinking": "你的分析与点评过程",
              "is_match": true或false,
              "reason": "简短判定理由"
            }
            """.formatted(activeQuestName, activeQuestId);
    }

    private static String getNormalPrompt(String localCandidateHint) {
        return """
        你是Minecraft原版像素图标识别模型。
        玩家在浅灰背景上绘制物品/方块/实体，请忽略淡灰底色，专注主体。
        玩家可能不是专业画手，可能会比较粗糙。

        【识别规则】
        - 先判轮廓和形状，定位类别（工具、方块、食物、生物、材料等）。
        - 再结合颜色和细节锁定具体物品。
        - 树的草图 → 可以输出树苗。
        - 无规律涂鸦 → 输出“线”。
        - 工具/装备看头部颜色判定材质，忽略棕色手柄：
          棕→木/皮革，浅灰/灰→石（网格状为锁链），
          白→铁，浅蓝→钻石，黄→金，黑→下界合金。
        - 附魔书：书状轮廓 + 紫色/粉色光效或魔法符文 → minecraft:enchanted_book。
        - 金制/附魔变种：证据不足时一律给普通版。
          比如附魔金苹果必须同时满足：
          a) 已确认是金苹果
          b) 覆盖明显的紫/蓝/粉紫光效层，仅描边不算。

        【优先使用本地候选】
        %s

        【严格 JSON（无代码块）】
        {
          "thinking": "分析过程",
          "name": "中文名",
          "type": "item/entity/block/unknown",
          "result": "minecraft:英文ID 或留空"
        }
        """.formatted(localCandidateHint);
    }

    // 结果验证与校准
    private static FinalResult parseAndValidateAiResult(AiResponse parsed, List<MatchCandidate> localMatches) {
        String aiRawId = normalizeId(parsed.result);
        String aiName = parsed.name == null ? "" : parsed.name.trim();
        String aiType = parsed.type == null ? "unknown" : parsed.type.trim().toLowerCase();
        String thinkingProcess = parsed.thinking == null ? "" : parsed.thinking;

        ToolType toolTypeByThinking = ToolType.identify(thinkingProcess);
        ToolType toolTypeById = ToolType.identify(aiRawId);
        ToolType toolTypeByName = ToolType.identify(aiName);

        if (toolTypeByThinking != null && toolTypeById != null && toolTypeByThinking != toolTypeById) {
            String material = extractMaterial(aiRawId);
            if (material != null) {
                String correctedId = "minecraft:" + material + "_" + toolTypeByThinking.keywords.get(0);
                if (isValidItemId(correctedId)) {
                    aiRawId = correctedId;
                    toolTypeById = toolTypeByThinking;
                }
            }
        }

        if (toolTypeByName != null && toolTypeById != null && toolTypeByName != toolTypeById) {
            FinalResult byName = searchByDisplayName(aiName, false);
            if (byName != null) return byName;
        }

        FinalResult aiResult = validateOrRepairId(aiRawId);
        if (aiResult != null && isValidIdDirectly(aiResult.id)) return aiResult;

        if (!aiName.isBlank()) {
            FinalResult byName = searchByDisplayName(aiName, "entity".equals(aiType));
            if (byName != null) return byName;
        }

        Set<String> aiKeywords = extractKeywords(thinkingProcess + " " + aiName);
        if (localMatches != null && !localMatches.isEmpty()) {
            for (MatchCandidate candidate : localMatches) {
                ToolType localType = ToolType.identify(candidate.id);
                if (toolTypeByThinking != null && localType != null && toolTypeByThinking != localType) {
                    continue;
                }
                if ((candidate.score >= 0.85 && isAiThinkingMatchingLocal(aiKeywords, candidate.id)) || candidate.score >= 0.92) {
                    return new FinalResult(candidate.id, false);
                }
            }
        }

        return new FinalResult(ThreadLocalRandom.current().nextBoolean() ? "minecraft:string" : "minecraft:bone_meal", false);
    }

    private static String extractMaterial(String id) {
        String[] materials = {"wooden", "stone", "iron", "golden", "diamond", "netherite"};
        for (String m : materials) {
            if (id.contains(m)) return m;
        }
        return null;
    }

    // 图像处理与本地匹配
    public static void preloadTexturesAsync() {
        if (ITEM_TEXTURES_LOADED || ITEM_TEXTURES_LOADING) return;
        CompletableFuture.runAsync(() -> ensureItemTexturesLoaded());
    }

    private static void ensureItemTexturesLoaded() {
        if (ITEM_TEXTURES_LOADED) return;
        synchronized (PaintProcessor.class) {
            if (ITEM_TEXTURES_LOADED) return;
            ITEM_TEXTURES_LOADING = true;
            Minecraft mc = Minecraft.getInstance();

            for (Identifier itemId : BuiltInRegistries.ITEM.keySet()) {
                Identifier textureId = resolveItemTexture(itemId);
                if (textureId == null) continue;
                mc.getResourceManager().getResource(textureId).ifPresent(res -> {
                    try (InputStream in = res.open()) {
                        BufferedImage image = ImageIO.read(in);
                        if (image != null) {
                            BufferedImage processed = fitToSquare(cropTransparentContent(image, 0), LOCAL_MATCH_TARGET_SIZE);
                            String idStr = itemId.toString();
                            ITEM_TEXTURE_CACHE.put(idStr, processed);
                            // 预计算颜色签名
                            TEXTURE_COLOR_SIGNATURE.put(idStr, computeColorSignature(processed));
                        }
                    } catch (Exception ignored) {}
                });
            }
            ITEM_TEXTURES_LOADED = true;
            ITEM_TEXTURES_LOADING = false;
        }
    }

    /**
     * 计算颜色签名：{ avgR, avgG, avgB, avgA, dominantCount } — 只统计非透明像素。
     */
    private static int[] computeColorSignature(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        long sumR = 0, sumG = 0, sumB = 0, sumA = 0;
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int a = (rgb >>> 24) & 0xFF;
                if (a >= 16) {
                    sumR += (rgb >> 16) & 0xFF;
                    sumG += (rgb >> 8) & 0xFF;
                    sumB += rgb & 0xFF;
                    sumA += a;
                    count++;
                }
            }
        }
        if (count == 0) return new int[]{0, 0, 0, 0, 0};
        return new int[]{(int)(sumR / count), (int)(sumG / count), (int)(sumB / count), (int)(sumA / count), count};
    }

    /**
     * 颜色签名快速距离：0~1，越小越相似。
     */
    private static double colorSignatureDistance(int[] sigA, int[] sigB) {
        if (sigA[4] == 0 || sigB[4] == 0) return 1.0;
        double dr = sigA[0] - sigB[0];
        double dg = sigA[1] - sigB[1];
        double db = sigA[2] - sigB[2];
        return Math.sqrt(dr * dr + dg * dg + db * db) / 441.67;
    }

    private static List<MatchCandidate> findBestLocalMatches(BufferedImage playerImage) {
        // 先计算玩家图像的颜色签名
        int[] playerSig = computeColorSignature(playerImage);

        // 颜色预过滤：先用签名快速筛选，再对候选做精确比较
        double preFilterThreshold = 0.55; // 颜色差异小于此值的候选进入精确比较
        return ITEM_TEXTURE_CACHE.entrySet().parallelStream()
                .filter(e -> {
                    int[] sig = TEXTURE_COLOR_SIGNATURE.get(e.getKey());
                    if (sig == null) return true; // 无签名时不过滤
                    return colorSignatureDistance(playerSig, sig) < preFilterThreshold;
                })
                .map(e -> new MatchCandidate(e.getKey(), compareImagesFast(playerImage, e.getValue())))
                .filter(c -> c.score >= LOCAL_MATCH_MIN_SCORE)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(LOCAL_TOP_CANDIDATES)
                .toList();
    }

    private static BufferedImage fitToSquare(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        int w = src.getWidth(), h = src.getHeight();
        if (w <= 0 || h <= 0) return out;

        double scale = Math.min((double) size / w, (double) size / h);
        int newW = Math.max(1, (int) Math.round(w * scale));
        int newH = Math.max(1, (int) Math.round(h * scale));
        int x = (size - newW) / 2, y = (size - newH) / 2;

        Graphics2D g2d = out.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(src, x, y, newW, newH, null);
        g2d.dispose();
        return out;
    }

    private static BufferedImage fillTransparentBackground(BufferedImage src, int bgColor) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = out.createGraphics();
        g2d.setColor(new Color(bgColor));
        g2d.fillRect(0, 0, src.getWidth(), src.getHeight());
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return out;
    }

    private static BufferedImage dynamicUpscale(BufferedImage src, int targetMaxDimension) {
        int maxDim = Math.max(src.getWidth(), src.getHeight());
        if (maxDim == 0) return src;
        int scale = Math.max(1, targetMaxDimension / maxDim);
        scale = Math.min(scale, 8);

        int w = src.getWidth() * scale, h = src.getHeight() * scale;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = out.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(src, 0, 0, w, h, null);
        g2d.dispose();
        return out;
    }

    private static BufferedImage cropTransparentContent(BufferedImage src, int padding) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] pixels = src.getRGB(0, 0, width, height, null, 0, width);

        int minX = width, minY = height, maxX = -1, maxY = -1;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (pixels[y * width + x] >>> 24) & 0xFF;
                if (alpha > 16) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX == -1) return src;

        minX = Math.max(0, minX - padding); minY = Math.max(0, minY - padding);
        maxX = Math.min(width - 1, maxX + padding); maxY = Math.min(height - 1, maxY + padding);

        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /**
     * 快速像素比较：采样步长 stride 减少计算量（32×32 已很小，但仍然可以跳过部分像素）。
     * stride=2 时只采样 1/4 的像素，对 32×32 的缩略图精度影响极小。
     */
    private static double compareImagesFast(BufferedImage a, BufferedImage b) {
        int w = Math.min(a.getWidth(), b.getWidth());
        int h = Math.min(a.getHeight(), b.getHeight());
        int stride = (w <= 16) ? 1 : 2; // 极小图全采样

        int[] pixelsA = a.getRGB(0, 0, w, h, null, 0, w);
        int[] pixelsB = b.getRGB(0, 0, w, h, null, 0, w);

        double score = 0, weight = 0;

        for (int y = 0; y < h; y += stride) {
            int rowBase = y * w;
            for (int x = 0; x < w; x += stride) {
                int i = rowBase + x;
                int rgbA = pixelsA[i];
                int rgbB = pixelsB[i];

                boolean emptyA = ((rgbA >>> 24) & 0xFF) < 16;
                boolean emptyB = ((rgbB >>> 24) & 0xFF) < 16;

                if (emptyA && emptyB) { score++; weight++; continue; }
                if (emptyA != emptyB) { weight += 1.2; continue; }

                int dr = ((rgbA >> 16) & 0xFF) - ((rgbB >> 16) & 0xFF);
                int dg = ((rgbA >> 8) & 0xFF) - ((rgbB >> 8) & 0xFF);
                int db = (rgbA & 0xFF) - (rgbB & 0xFF);

                double colorScore = 1.0 - Math.sqrt(dr * dr + dg * dg + db * db) / 441.67;
                score += Math.max(0, colorScore) * 1.5;
                weight += 1.5;
            }
        }
        return weight <= 0 ? 0 : score / weight;
    }

    private static String buildLocalCandidateHint(List<MatchCandidate> localMatches) {
        if (localMatches == null || localMatches.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("本地匹配候选：\n");
        for (int i = 0; i < localMatches.size(); i++) {
            MatchCandidate c = localMatches.get(i);
            sb.append(i + 1).append(". ").append(getItemDisplayName(c.id))
                    .append(" (").append(c.id).append(", 相似度 ").append(String.format("%.1f%%", c.score * 100)).append(")\n");
        }
        return sb.toString();
    }

    // 书与笔写入与 ID 辅助方法
    private static boolean writeThinkingToWritableBook(String log) {
        var player = Minecraft.getInstance().player;
        if (player == null || log == null || log.isBlank()) return false;

        WritableBookContent content = new WritableBookContent(splitBookPages(log, 130));
        var inventory = player.getInventory();

        return Stream.concat(Stream.of(player.getMainHandItem(), player.getOffhandItem()), inventory.getNonEquipmentItems().stream())
                .filter(stack -> !stack.isEmpty() && stack.is(Items.WRITABLE_BOOK))
                .findFirst()
                .map(stack -> {
                    stack.set(DataComponents.WRITABLE_BOOK_CONTENT, content);
                    inventory.setChanged();
                    return true;
                }).orElse(false);
    }

    private static List<Filterable<String>> splitBookPages(String text, int dummy) {
        var font = Minecraft.getInstance().font;

        final int MAX_WIDTH = 114;
        final int MAX_LINES = 14;

        List<Filterable<String>> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();

        int currentLines = 0;

        for (String rawLine : text.replace("\r", "").split("\n")) {

            StringBuilder lineBuilder = new StringBuilder();

            for (char c : rawLine.toCharArray()) {
                lineBuilder.append(c);

                if (font.width(lineBuilder.toString()) > MAX_WIDTH) {
                    lineBuilder.setLength(lineBuilder.length() - 1);

                    if (page.length() > 0) page.append("\n");
                    page.append(lineBuilder);

                    currentLines++;

                    if (currentLines >= MAX_LINES) {
                        pages.add(Filterable.passThrough(page.toString()));
                        page.setLength(0);
                        currentLines = 0;
                    }

                    lineBuilder = new StringBuilder().append(c);
                }
            }

            if (lineBuilder.length() > 0) {
                if (page.length() > 0) page.append("\n");
                page.append(lineBuilder);
                currentLines++;

                if (currentLines >= MAX_LINES) {
                    pages.add(Filterable.passThrough(page.toString()));
                    page.setLength(0);
                    currentLines = 0;
                }
            }
        }

        if (!page.isEmpty()) {
            pages.add(Filterable.passThrough(page.toString()));
        }

        if (pages.isEmpty()) {
            pages.add(Filterable.passThrough("无分析结果"));
        }

        return pages;
    }

    private static FinalResult validateOrRepairId(String rawId) {
        if (rawId == null || rawId.isEmpty()) return null;
        if (isValidItemId(rawId)) {
            String eggEntity = tryConvertEggToEntity(rawId);
            return eggEntity != null ? new FinalResult(eggEntity, true) : new FinalResult(rawId, false);
        }
        if (isValidEntityId(rawId)) return new FinalResult(rawId, true);
        String eggEntity = tryConvertEggToEntity(rawId);
        if (eggEntity != null) return new FinalResult(eggEntity, true);

        String rawName = rawId.replace("minecraft:", "").replace("_", "");
        for (Identifier id : BuiltInRegistries.ITEM.keySet()) if (id.getPath().replace("_", "").equals(rawName)) return new FinalResult(id.toString(), false);
        for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) if (id.getPath().replace("_", "").equals(rawName)) return new FinalResult(id.toString(), true);
        return null;
    }

    private static FinalResult searchByDisplayName(String name, boolean isEntity) {
        if (name == null || name.isBlank()) return null;
        String kw = name.toLowerCase().replace(" ", "");
        var registry = isEntity ? BuiltInRegistries.ENTITY_TYPE : BuiltInRegistries.ITEM;

        return registry.keySet().stream()
                .max(Comparator.comparingInt(id -> scoreTextMatch(kw, getDisplayName(id.toString(), isEntity), id.toString())))
                .filter(id -> scoreTextMatch(kw, getDisplayName(id.toString(), isEntity), id.toString()) >= 2)
                .map(id -> new FinalResult(id.toString(), isEntity))
                .orElse(null);
    }

    private static int scoreTextMatch(String keyword, String displayName, String id) {
        String n1 = displayName.toLowerCase().replace(" ", ""), n2 = id.replace("minecraft:", "").replace("_", "");
        int score = 0;
        if (n1.equals(keyword) || n2.equals(keyword)) score += 1000;
        else if (n1.contains(keyword) || n2.contains(keyword)) score += 50;
        return score;
    }

    private static Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        if (text != null) {
            for (ToolType type : ToolType.values()) {
                for (String kw : type.keywords) if (text.toLowerCase().contains(kw)) keywords.add(kw);
            }
        }
        return keywords;
    }

    private static boolean isAiThinkingMatchingLocal(Set<String> keywords, String localId) {
        String idLower = localId.toLowerCase();
        return keywords.stream().anyMatch(idLower::contains);
    }

    private static String tryConvertEggToEntity(String id) {
        if (id != null && id.endsWith("_spawn_egg")) {
            String entityId = id.replace("_spawn_egg", "");
            if (isValidEntityId(entityId)) return entityId;
        }
        return null;
    }

    private static Identifier resolveItemTexture(Identifier itemId) {
        var resMgr = Minecraft.getInstance().getResourceManager();
        return Stream.of("item", "block")
                .map(type -> Identifier.tryParse(itemId.getNamespace() + ":textures/" + type + "/" + itemId.getPath() + ".png"))
                .filter(id -> id != null && resMgr.getResource(id).isPresent())
                .findFirst().orElse(null);
    }

    private static String normalizeId(String idText) {
        if (idText == null) return "";
        String s = idText.trim().toLowerCase().replace("：", ":").replace(" ", "").replace("`", "");
        Matcher m = ID_PATTERN.matcher(s);
        return m.find() ? m.group(1) : (s.matches("[a-z0-9_]+") ? "minecraft:" + s : "");
    }

    private static boolean isValidItemId(String id) { return id != null && BuiltInRegistries.ITEM.containsKey(Identifier.tryParse(id)); }
    private static boolean isValidEntityId(String id) { return id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.tryParse(id)); }
    private static boolean isValidIdDirectly(String id) { return isValidItemId(id) || isValidEntityId(id); }

    private static String getDisplayName(String id, boolean isEntity) { return isEntity ? getEntityDisplayName(id) : getItemDisplayName(id); }
    private static String getItemDisplayName(String id) {
        try { return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.tryParse(id))).getHoverName().getString(); } catch (Exception e) { return id; }
    }
    private static String getEntityDisplayName(String id) {
        try { return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.tryParse(id)).getDescription().getString(); } catch (Exception e) { return id; }
    }

    // 网络通信与状态管理
    private static void giveResultToServer(String id, boolean isEntity) {
        if (Minecraft.getInstance().getConnection() != null) Minecraft.getInstance().getConnection().send(new GiveResultPayload(id, isEntity));
    }
    private static void msg(String s) { if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(s), false); }

    private static void executeInMainThread(Runnable runnable) {
        Minecraft.getInstance().execute(runnable);
    }

    public static void setActiveQuestFromServer(String questId, String questName, boolean isEntity) {
        if (questId == null || questId.isBlank()) {
            clearActiveQuestFromServer();
            return;
        }
        activeQuestId = questId;
        activeQuestName = questName;
        activeQuestIsEntity = isEntity;

        String display = getActiveQuestDisplayName();
        msg("§d某种力量在召唤你描绘：§e" + display);
    }

    public static String getActiveQuestDisplayName() {
        if (activeQuestId == null || activeQuestId.isBlank()) return "未知物品";
        // 自定义任务直接使用服务端名称
        if (activeQuestId.startsWith("custom:")) {
            return activeQuestName != null ? activeQuestName : activeQuestId;
        }
        return activeQuestIsEntity ? getEntityDisplayName(activeQuestId) : getItemDisplayName(activeQuestId);
    }

    public static void clearActiveQuestFromServer() {
        activeQuestId = null;
        activeQuestName = null;
        activeQuestIsEntity = false;
    }

    // 工具类型枚举
    enum ToolType {
        PICKAXE("pickaxe", "镐"), AXE("axe", "斧"), SWORD("sword", "剑"), SHOVEL("shovel", "锹", "铲"), HOE("hoe", "锄");

        final List<String> keywords;
        ToolType(String... kws) { this.keywords = List.of(kws); }

        static ToolType identify(String text) {
            if (text == null) return null;
            String lower = text.toLowerCase();

            if (lower.contains("pickaxe") || lower.contains("镐")) return PICKAXE;
            if (lower.matches(".*\\baxe\\b.*") || lower.contains("_axe") || lower.contains("斧")) return AXE;
            if (lower.contains("sword") || lower.contains("剑")) return SWORD;
            if (lower.contains("shovel") || lower.contains("锹") || lower.contains("铲")) return SHOVEL;
            if (lower.contains("hoe") || lower.contains("锄")) return HOE;
            return null;
        }
    }

    private static void sendMapToServer(BufferedImage rawImage, String itemName) {
        if (Minecraft.getInstance().getConnection() != null) {
            int[] pixels = new int[64 * 64];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    pixels[y * 64 + x] = (x < rawImage.getWidth() && y < rawImage.getHeight())
                            ? rawImage.getRGB(x, y)
                            : 0x00000000;
                }
            }
            Minecraft.getInstance().getConnection()
                    .send(new DrawSaveMapPayload(pixels, itemName));
        }
    }

    record MatchCandidate(String id, double score) {}
    record FinalResult(String id, boolean isEntity) {}

    record AiResponse(String thinking, String name, String type, String result, Boolean is_match, String reason) {}
}