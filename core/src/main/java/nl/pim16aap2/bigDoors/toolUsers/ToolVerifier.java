package nl.pim16aap2.bigDoors.toolUsers;

import javax.annotation.Nullable;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

public class ToolVerifier
{
    private String toolName;

    public ToolVerifier(String str)
    {
        toolName = str;
    }

    // Check if the provided itemstack is a selection tool.
    public boolean isTool(@Nullable ItemStack is)
    {
        return  is != null                                        &&
                is.getType() == Material.STICK                    &&
                is.getEnchantmentLevel(Enchantment.LUCK) == 1     &&
                is.getItemMeta().getDisplayName() != null         &&
                is.getItemMeta().getDisplayName().equals(toolName);
    }
}
