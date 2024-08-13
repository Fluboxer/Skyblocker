package de.hysky.skyblocker.skyblock.item.tooltip.adders;

import com.google.gson.JsonObject;
import de.hysky.skyblocker.config.SkyblockerConfigManager;
import de.hysky.skyblocker.skyblock.item.tooltip.TooltipInfoType;
import de.hysky.skyblocker.skyblock.item.tooltip.TooltipManager;
import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.container.SimpleContainerSolver;
import de.hysky.skyblocker.utils.container.TooltipAdder;
import de.hysky.skyblocker.utils.render.gui.ColorHighlight;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipAppender;
import de.hysky.skyblocker.utils.container.TooltipAdder;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BitsTooltip extends SimpleContainerSolver implements TooltipAdder {
    private static final Pattern BITS_PATTERN = Pattern.compile("Cost (?<amount>[\\d,]+) Bits");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("Click to browse!");
    private static final NumberFormat DECIMAL_FORMAT = NumberFormat.getInstance(Locale.US);
    private static final Logger LOGGER = LoggerFactory.getLogger("Skyblocker Bits");

    public static final BitsTooltip INSTANCE = new BitsTooltip();

    public BitsTooltip() {
        super(".*(?:Community Shop|Bits Shop).*");
    }

    @Override
    public boolean isEnabled() {
        return SkyblockerConfigManager.get().general.itemTooltip.showBitsCost;
    }

    double getPrice(ItemStack stack) {
        double itemCost = 0;
        String itemName = stack.getSkyblockApiId();

        if (TooltipInfoType.BAZAAR.getData().has(itemName)) {
            itemCost = TooltipInfoType.BAZAAR.getData().getAsJsonObject(stack.getSkyblockApiId()).get("buyPrice").getAsDouble();
        } else if (TooltipInfoType.LOWEST_BINS.getData().has(itemName)) {
            itemCost = TooltipInfoType.LOWEST_BINS.getData().get(stack.getSkyblockApiId()).getAsDouble();
        }

        return itemCost;
    }

    double getPrice(String itemName) {
        double itemCost = 0;

        if (TooltipInfoType.BAZAAR.getData().has(itemName)) {
            itemCost = TooltipInfoType.BAZAAR.getData().getAsJsonObject(itemName).get("buyPrice").getAsDouble();
        } else if (TooltipInfoType.LOWEST_BINS.getData().has(itemName)) {
            itemCost = TooltipInfoType.LOWEST_BINS.getData().get(itemName).getAsDouble();
        }

        return itemCost;
    }

    @Override
    public List<ColorHighlight> getColors(Int2ObjectMap<ItemStack> slots) {
        List<ColorHighlight> highlights = new ArrayList<>();
        double bestCoinsPerBit = 0;
        int bestSlotIndex = -1;


        for (Int2ObjectMap.Entry<ItemStack> entry : slots.int2ObjectEntrySet()) {
            ItemStack stack = entry.getValue();
            if (stack == null || stack.isEmpty()) continue;

            if (!CATEGORY_PATTERN.matcher(ItemUtils.concatenateLore(ItemUtils.getLore(stack))).find()) {
                String lore = ItemUtils.concatenateLore(ItemUtils.getLore(stack));
                Matcher bitsMatcher = BITS_PATTERN.matcher(lore);
                if (!bitsMatcher.find()) continue;
                long bitsCost = Long.parseLong(bitsMatcher.group("amount").replace(",", ""));
                double itemCost = getPrice(stack)*stack.getCount();

                if (itemCost == 0) continue;

                long coinsPerBit = Math.round(itemCost / bitsCost);
                LOGGER.info("Coins per bit for {}: {}", stack.getSkyblockApiId(), coinsPerBit);

                if (coinsPerBit > bestCoinsPerBit) {
                    bestCoinsPerBit = coinsPerBit;
                    bestSlotIndex = entry.getIntKey();
                }
            } else {
                LOGGER.info("Detected category!");

                long coinsPerBit = processCategory(stack);

                if (coinsPerBit > bestCoinsPerBit) {
                    bestCoinsPerBit = coinsPerBit;
                    bestSlotIndex = entry.getIntKey();
                }
            }
        }
        if (bestSlotIndex != -1) {
            highlights.add(ColorHighlight.green(bestSlotIndex));
        }
        return highlights;
    }

    @Override
    public void addToTooltip(@Nullable Slot focusedSlot, ItemStack stack, List<Text> lines) {
        if (focusedSlot == null) return;

        String lore = ItemUtils.concatenateLore(lines);
        Matcher bitsMatcher = BITS_PATTERN.matcher(lore);
        if (!bitsMatcher.find()) {
//            LOGGER.info("No bits pattern found in lore for item: {}", stack.getSkyblockApiId());
            return;
        }

        long bitsCost = Long.parseLong(bitsMatcher.group("amount").replace(",", ""));

        double itemCost = getPrice(stack)*stack.getCount();
        if (itemCost == 0) {
//            LOGGER.info("Item cost is zero for {}", stack.getSkyblockApiId());
            return;
        }

        long coinsPerBit = Math.round(itemCost / bitsCost);
//        LOGGER.info("Coins per bit for {}: {}", stack.getSkyblockApiId(), coinsPerBit);

        lines.add(Text.empty()
                .append(Text.literal("Bits Cost: ").formatted(Formatting.AQUA))
                .append(Text.literal(DECIMAL_FORMAT.format(coinsPerBit) + " Coins per bit").formatted(Formatting.DARK_AQUA))
        );

    }

    private long processCategory(ItemStack stack) {
        String categoryName = stack.getName().getString();
        LOGGER.info("Detected category name: {}", categoryName);
        if (categories.containsKey(categoryName)) {
            LOGGER.info("Key matched for: {}", categoryName);
            Map<String, Long> results = new HashMap<>();
            long bestResult = 0;
            String bestItemID = "";

            Map<String, Integer> category = categories.get(categoryName);
            for (Map.Entry<String, Integer> entry : category.entrySet()) {
                String itemID = entry.getKey(); // ID предмета
                Integer itemBitsPrice = entry.getValue(); // цена в битах
                double itemCost = getPrice(itemID);
                LOGGER.info("Line processed: {} item, {} price in bits, {} price in coins", itemID, itemBitsPrice, itemCost);
                long roundedValue = Math.round(itemCost / itemBitsPrice);
                results.put(itemID, roundedValue);
                if (roundedValue > bestResult) {
                    bestResult = roundedValue;
                    bestItemID = itemID; // Сохраняем ID предмета с наибольшим значением
                }
            }
            LOGGER.info("Best item: {} with value: {}", bestItemID, bestResult);
            return bestResult;
        } else if (categoryName.contains("Fuel Blocks")) {  // TODO: add blaze slayer 9 discount support
            LOGGER.info("Fuel Blocks code triggered");
            String itemID = "INFERNO_FUEL_BLOCK";
            int[] itemBitsPrice = {75, 3600};
            double itemCost = getPrice(itemID);
            return (long) (Math.max(itemCost / itemBitsPrice[0], itemCost * 64 / itemBitsPrice[1]));    // this is semi-pointless as stack should be ALWAYS better
        } else {
            LOGGER.warn("For {} key was NOT matched!", categoryName);
        }
        return 0;
    }

    // SKYBLOCK_ID, Bits Price. Actual data on 13.08.2024
    private final Map<String, Integer> catKat = Util.make(new HashMap<>(), map -> {
        map.put("KAT_FLOWER", 500);
        map.put("KAT_BOUQUET", 2500);
    });

    private final Map<String, Integer> catUpgradeComponents = Util.make(new HashMap<>(), map -> {
        map.put("HEAT_CORE", 3000);
        map.put("HYPER_CATALYST_UPGRADE", 300);
        map.put("ULTIMATE_CARROT_CANDY_UPGRADE", 8000);
        map.put("COLOSSAL_EXP_BOTTLE_UPGRADE", 1200);
        map.put("JUMBO_BACKPACK_UPGRADE", 4000);
        map.put("MINION_STORAGE_EXPANDER", 1500);
    });

    private final Map<String, Integer> catSacks = Util.make(new HashMap<>(), map -> {
        map.put("POCKET_SACK_IN_A_SACK", 8000);
        map.put("LARGE_DUNGEON_SACK", 14000);   //sacks can't be traded, but I will add them anyway in case they will be auctionable at some point
        map.put("RUNE_SACK", 14000);
        map.put("FLOWER_SACK", 14000);
        map.put("DWARVEN_MINES_SACK", 14000);
        map.put("CRYSTAL_HOLLOWS_SACK", 14000);
    });

    private final Map<String, Integer> catAbiphone = Util.make(new HashMap<>(), map -> {
        map.put("TRIO_CONTACTS_ADDON", 6450);
        map.put("ABICASE", 15000);      // Original skibidiblock ID is all "ABICASE" with "Model" extra data. I don't see any good reason to do it like this. Admins why
//        map.put("ABICASE_SUMSUNG_2", 25000);  // Luckily they all suck for bits to coins anyway
//        map.put("ABICASE_REZAR", 26000);  // Speaking of which, out lbins for abicases are also cooked for same reason
//        map.put("ABICASE_BLUE_AQUA", 17000);
//        map.put("ABICASE_BLUE_BLUE", 17000);
//        map.put("ABICASE_BLUE_GREEN", 17000);
//        map.put("ABICASE_BLUE_RED", 17000);
//        map.put("ABICASE_BLUE_YELLOW", 17000);
    });

    private final Map<String, Integer> catDyes = Util.make(new HashMap<>(), map -> {
        map.put("DYE_PURE_WHITE", 250000);
        map.put("DYE_PURE_BLACK", 250000);
    });

    private final Map<String, Integer> catEnchants = Util.make(new HashMap<>(), map -> {
        map.put("ENCHANTMENT_HECATOMB_1", 6000);
        map.put("ENCHANTMENT_EXPERTISE_1", 4000);
        map.put("ENCHANTMENT_COMPACT_1", 4000);
        map.put("ENCHANTMENT_CULTIVATING_1", 4000);
        map.put("ENCHANTMENT_CHAMPION_1", 4000);
        map.put("ENCHANTMENT_TOXOPHILITE_1", 4000);
    });

    private final Map<String, Integer> catEnrichments = Util.make(new HashMap<>(), map -> {
        map.put("TALISMAN_ENRICHMENT_SWAPPER", 200);
        map.put("TALISMAN_ENRICHMENT_WALK_SPEED", 5000);
        map.put("TALISMAN_ENRICHMENT_INTELLIGENCE", 5000);
        map.put("TALISMAN_ENRICHMENT_CRITICAL_DAMAGE", 5000);
        map.put("TALISMAN_ENRICHMENT_CRITICAL_CHANCE", 5000);
        map.put("TALISMAN_ENRICHMENT_STRENGTH", 5000);
        map.put("TALISMAN_ENRICHMENT_DEFENSE", 5000);
        map.put("TALISMAN_ENRICHMENT_HEALTH", 5000);
        map.put("TALISMAN_ENRICHMENT_MAGIC_FIND", 5000);
        map.put("TALISMAN_ENRICHMENT_FEROCITY", 5000);
        map.put("TALISMAN_ENRICHMENT_SEA_CREATURE_CHANCE", 5000);
        map.put("TALISMAN_ENRICHMENT_ATTACK_SPEED", 5000);
    });

    private final Map<String, Map<String, Integer>> categories = Util.make(new HashMap<>(), map -> {
        // Категории
        map.put("Kat Items", catKat);
        map.put("Upgrade Components", catUpgradeComponents);
        map.put("Sacks", catSacks);
        map.put("Abiphone Supershop", catAbiphone);
        map.put("Dyes", catDyes);
        map.put("Stacking Enchants", catEnchants);
        map.put("Enrichments", catEnrichments);
    });

    @Override
    public int getPriority() {
        return 0; // Intended to show first
    }
}
