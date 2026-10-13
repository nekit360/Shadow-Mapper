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

    /**
     * Whether the texture behind a GL id carries an alpha channel that is used as transparency.
     *
     * The renderer needs this to decide whether a material belongs in its transparent pass, and the resolver
     * is the only one that has held the texture itself (see {@code Texture.alpha()}).
     *
     * @param glTextureId a GL texture id this resolver handed out
     * @return true when that texture's alpha channel is transparent, false when it is opaque one
     */
    default boolean isTransparentTexture(int glTextureId) {
        return false;
    }
}
