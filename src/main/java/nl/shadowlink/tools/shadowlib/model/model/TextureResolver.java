package nl.shadowlink.tools.shadowlib.model.model;

import nl.shadowlink.tools.shadowlib.texturedic.TextureDic;

/**
 * Supplies GL texture ids for the textures a model asks for by name.
 *
 * The models know nothing about GL: they hand the name a shader wants to the renderer, which decides
 * where that name lives (the model's own dictionary, the TXD of its .ide entry, or a cache) and
 * uploads it once.
 *
 * @author Shadow-Link
 */
public interface TextureResolver {

    /**
     * @param dictionary  a dictionary attached to the element the name is resolved for
     * @param textureName the texture name the shader asks for
     * @return the GL texture id of that texture, or 0 when this dictionary does not have it
     */
    int resolve(TextureDic dictionary, String textureName);
}
