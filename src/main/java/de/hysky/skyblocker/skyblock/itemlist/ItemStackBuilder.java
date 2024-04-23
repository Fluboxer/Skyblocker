package de.hysky.skyblocker.skyblock.itemlist;

import de.hysky.skyblocker.utils.ItemUtils;
import de.hysky.skyblocker.utils.NEURepoManager;
import io.github.moulberry.repo.constants.PetNumbers;
import io.github.moulberry.repo.data.NEUItem;
import io.github.moulberry.repo.data.Rarity;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ItemStackBuilder {
    private static Map<String, Map<Rarity, PetNumbers>> petNums;

    public static void loadPetNums() {
        try {
            petNums = NEURepoManager.NEU_REPO.getConstants().getPetNumbers();
        } catch (Exception e) {
            ItemRepository.LOGGER.error("Failed to load petnums.json");
        }
    }

    public static ItemStack fromNEUItem(NEUItem item) {
        String internalName = item.getSkyblockItemId();

        List<Pair<String, String>> injectors = new ArrayList<>(petData(internalName));

        String legacyId = item.getMinecraftItemId();
        Identifier itemId = new Identifier(ItemFixerUpper.convertItemId(legacyId, item.getDamage()));
        
        ItemStack stack = new ItemStack(Registries.ITEM.get(itemId));

        // Create & Attach ExtraAttributes tag
        NbtCompound customData = new NbtCompound();

        // Add Skyblock Item Id
        customData.put(ItemUtils.ID, NbtString.of(internalName));

        // Item Name
        String name = injectData(item.getDisplayName(), injectors);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.of(name));

        // Lore
        stack.set(DataComponentTypes.LORE, new LoreComponent(item.getLore().stream().map(line -> Text.of(injectData(line, injectors))).collect(Collectors.toCollection(() -> new ArrayList<>()))));

        String nbttag = item.getNbttag();
        // add skull texture
        Matcher skullUuid = Pattern.compile("(?<=SkullOwner:\\{)Id:\"(.{36})\"").matcher(nbttag);
        Matcher skullTexture = Pattern.compile("(?<=Properties:\\{textures:\\[0:\\{Value:)\"(.+?)\"").matcher(nbttag);
        if (skullUuid.find() && skullTexture.find()) {
            UUID uuid = UUID.fromString(skullUuid.group(1));
            String textureValue = skullTexture.group(1);

            stack.set(DataComponentTypes.PROFILE, new ProfileComponent(Optional.of(internalName), Optional.of(uuid), ItemUtils.propertyMapWithTexture(textureValue)));
        }

        // add leather armor dye color
        Matcher colorMatcher = Pattern.compile("color:(\\d+)").matcher(nbttag);
        if (colorMatcher.find()) {
            int color = Integer.parseInt(colorMatcher.group(1));
            stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, false));
        }
        // add enchantment glint
        if (nbttag.contains("ench:")) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        
        //Hide weapon damage and other useless info
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, new AttributeModifiersComponent(List.of(), false));

        // Add firework star color
        Matcher explosionColorMatcher = Pattern.compile("\\{Explosion:\\{(?:Type:[0-9a-z]+,)?Colors:\\[(?<color>[0-9]+)]\\}").matcher(nbttag);
        if (explosionColorMatcher.find()) {
            //Forget about the actual ball type because it probably doesn't matter
            stack.set(DataComponentTypes.FIREWORK_EXPLOSION, new FireworkExplosionComponent(FireworkExplosionComponent.Type.SMALL_BALL, new IntArrayList(Integer.parseInt(explosionColorMatcher.group("color"))), new IntArrayList(), false, false));
        }
        
        // Attach custom nbt data
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(customData));

        return stack;
    }

    private static List<Pair<String, String>> petData(String internalName) {
        List<Pair<String, String>> list = new ArrayList<>();

        String petName = internalName.split(";")[0];
        if (!internalName.contains(";") || !petNums.containsKey(petName)) return list;

        final Rarity[] rarities = {
                Rarity.COMMON,
                Rarity.UNCOMMON,
                Rarity.RARE,
                Rarity.EPIC,
                Rarity.LEGENDARY,
                Rarity.MYTHIC,
        };
        Rarity rarity = rarities[Integer.parseInt(internalName.split(";")[1])];
        PetNumbers data = petNums.get(petName).get(rarity);

        int minLevel = data.getLowLevel();
        int maxLevel = data.getHighLevel();
        list.add(new Pair<>("\\{LVL\\}", minLevel + " ➡ " + maxLevel));

        Map<String, Double> statNumsMin = data.getStatsAtLowLevel().getStatNumbers();
        Map<String, Double> statNumsMax = data.getStatsAtHighLevel().getStatNumbers();
        Set<Map.Entry<String, Double>> entrySet = statNumsMin.entrySet();
        for (Map.Entry<String, Double> entry : entrySet) {
            String key = entry.getKey();
            String left = "\\{" + key + "\\}";
            String right = statNumsMin.get(key) + " ➡ " + statNumsMax.get(key);
            list.add(new Pair<>(left, right));
        }

        List<Double> otherNumsMin = data.getStatsAtLowLevel().getOtherNumbers();
        List<Double> otherNumsMax = data.getStatsAtHighLevel().getOtherNumbers();
        for (int i = 0; i < otherNumsMin.size(); ++i) {
            String left = "\\{" + i + "\\}";
            String right = otherNumsMin.get(i) + " ➡ " + otherNumsMax.get(i);
            list.add(new Pair<>(left, right));
        }

        return list;
    }

    private static String injectData(String string, List<Pair<String, String>> injectors) {
        for (Pair<String, String> injector : injectors) {
            string = string.replaceAll(injector.getLeft(), injector.getRight());
        }
        return string;
    }
}
