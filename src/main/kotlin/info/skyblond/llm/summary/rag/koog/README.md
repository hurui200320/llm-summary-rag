# Koog workarounds

This package contains workarounds for Koog.

Currently:

+ https://github.com/JetBrains/koog/issues/1624
  + https://github.com/JetBrains/koog/pull/1626
  + The OpenRouter is not usable since `additionalProperties` cannot be flattened properly,
    which means you can't set customized properties like `reasoning.effort`.
  + To fix this, I have to basically copy the whole OpenRouter related stuff into my own codebase
    and make modifications.
