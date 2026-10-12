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
     * @param dictionary  the dictionary to look in, or null to look anywhere the resolver knows - the
     *                    caller has nothing left to offer and the name has to be resolved the way the game
     *                    does it, against everything that is loaded
     * @param textureName the texture name the shader asks for
     * @return the GL texture id of that texture, or 0 when it cannot be found
     */
    int resolve(TextureDic dictionary, String textureName);
}
