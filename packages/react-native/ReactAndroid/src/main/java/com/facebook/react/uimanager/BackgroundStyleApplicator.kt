/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.uimanager

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import androidx.annotation.ColorInt
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.annotations.UnstableReactNativeAPI
import com.facebook.react.uimanager.PixelUtil.dpToPx
import com.facebook.react.uimanager.PixelUtil.pxToDp
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.common.ViewUtil
import com.facebook.react.uimanager.drawable.CSSBackgroundDrawable
import com.facebook.react.uimanager.drawable.CompositeBackgroundDrawable
import com.facebook.react.uimanager.drawable.InsetBoxShadowDrawable
import com.facebook.react.uimanager.drawable.MIN_INSET_BOX_SHADOW_SDK_VERSION
import com.facebook.react.uimanager.drawable.MIN_OUTSET_BOX_SHADOW_SDK_VERSION
import com.facebook.react.uimanager.drawable.OutlineDrawable
import com.facebook.react.uimanager.drawable.OutsetBoxShadowDrawable
import com.facebook.react.uimanager.style.BackgroundImageLayer
import com.facebook.react.uimanager.style.BorderInsets
import com.facebook.react.uimanager.style.BorderRadiusProp
import com.facebook.react.uimanager.style.BorderRadiusStyle
import com.facebook.react.uimanager.style.BorderStyle
import com.facebook.react.uimanager.style.BoxShadow
import com.facebook.react.uimanager.style.LogicalEdge
import com.facebook.react.uimanager.style.OutlineStyle

/**
 * BackgroundStyleApplicator is responsible for applying backgrounds, borders, and related effects,
 * to an Android view
 */
@OptIn(UnstableReactNativeAPI::class)
public object BackgroundStyleApplicator {

  @JvmStatic
  public fun setBackgroundColor(view: View, @ColorInt color: Int?): Unit {
    // No color to set, and no color already set
    if ((color == null || color == Color.TRANSPARENT) &&
        view.background !is CompositeBackgroundDrawable) {
      return
    }

    ensureCSSBackground(view).color = color ?: Color.TRANSPARENT
  }

  @JvmStatic
  public fun setBackgroundImage(
      view: View,
      backgroundImageLayers: List<BackgroundImageLayer>?
  ): Unit {
    ensureCSSBackground(view).setBackgroundImage(backgroundImageLayers)
  }

  @JvmStatic
  @ColorInt
  public fun getBackgroundColor(view: View): Int? = getCSSBackground(view)?.color

  @JvmStatic
  public fun setBorderWidth(view: View, edge: LogicalEdge, width: Float?): Unit {
    ensureCSSBackground(view).setBorderWidth(edge.toSpacingType(), width?.dpToPx() ?: Float.NaN)

    if (Build.VERSION.SDK_INT >= MIN_INSET_BOX_SHADOW_SDK_VERSION) {
      val composite = ensureCompositeBackgroundDrawable(view)
      composite.borderInsets = composite.borderInsets ?: BorderInsets()
      composite.borderInsets?.setBorderWidth(edge, width)

      for (shadow in composite.innerShadows) {
        (shadow as InsetBoxShadowDrawable).borderInsets = composite.borderInsets
        shadow.invalidateSelf()
      }
    }
  }

  @JvmStatic
  public fun getBorderWidth(view: View, edge: LogicalEdge): Float? {
    val width = getCSSBackground(view)?.getBorderWidth(edge.toSpacingType())
    return if (width == null || width.isNaN()) null else width.pxToDp()
  }

  @JvmStatic
  public fun setBorderColor(view: View, edge: LogicalEdge, @ColorInt color: Int?): Unit =
      ensureCSSBackground(view).setBorderColor(edge.toSpacingType(), color)

  @JvmStatic
  @ColorInt
  public fun getBorderColor(view: View, edge: LogicalEdge): Int? =
      getCSSBackground(view)?.getBorderColor(edge.toSpacingType())

  @JvmStatic
  public fun setBorderRadius(
      view: View,
      corner: BorderRadiusProp,
      radius: LengthPercentage?
  ): Unit {
    ensureCSSBackground(view).setBorderRadius(corner, radius)
    val compositeBackgroundDrawable = ensureCompositeBackgroundDrawable(view)
    compositeBackgroundDrawable.borderRadius =
        compositeBackgroundDrawable.borderRadius ?: BorderRadiusStyle()
    compositeBackgroundDrawable.borderRadius?.set(corner, radius)

    if (Build.VERSION.SDK_INT >= MIN_OUTSET_BOX_SHADOW_SDK_VERSION) {
      for (shadow in compositeBackgroundDrawable.outerShadows) {
        if (shadow is OutsetBoxShadowDrawable) {
          shadow.borderRadius = compositeBackgroundDrawable.borderRadius
        }
      }
    }

    if (Build.VERSION.SDK_INT >= MIN_INSET_BOX_SHADOW_SDK_VERSION) {
      for (shadow in compositeBackgroundDrawable.innerShadows) {
        if (shadow is InsetBoxShadowDrawable) {
          shadow.borderRadius = compositeBackgroundDrawable.borderRadius
        }
      }
    }

    compositeBackgroundDrawable.outline?.borderRadius = compositeBackgroundDrawable.borderRadius
    compositeBackgroundDrawable.invalidateSelf()
  }

  @JvmStatic
  public fun getBorderRadius(view: View, corner: BorderRadiusProp): LengthPercentage? =
      getCSSBackground(view)?.borderRadius?.get(corner)

  @JvmStatic
  public fun setBorderStyle(view: View, borderStyle: BorderStyle?): Unit {
    ensureCSSBackground(view).borderStyle = borderStyle
  }

  @JvmStatic
  public fun getBorderStyle(view: View): BorderStyle? = getCSSBackground(view)?.borderStyle

  @JvmStatic
  public fun setOutlineColor(view: View, @ColorInt outlineColor: Int?): Unit {
    if (ViewUtil.getUIManagerType(view) != UIManagerType.FABRIC) {
      return
    }

    val outline = ensureOutlineDrawable(view)
    if (outlineColor != null) {
      outline.outlineColor = outlineColor
    }
  }

  @JvmStatic public fun getOutlineColor(view: View): Int? = getOutlineDrawable(view)?.outlineColor

  @JvmStatic
  public fun setOutlineOffset(view: View, outlineOffset: Float): Unit {
    if (ViewUtil.getUIManagerType(view) != UIManagerType.FABRIC) {
      return
    }

    val outline = ensureOutlineDrawable(view)
    outline.outlineOffset = outlineOffset.dpToPx()
  }

  public fun getOutlineOffset(view: View): Float? = getOutlineDrawable(view)?.outlineOffset

  @JvmStatic
  public fun setOutlineStyle(view: View, outlineStyle: OutlineStyle?): Unit {
    if (ViewUtil.getUIManagerType(view) != UIManagerType.FABRIC) {
      return
    }

    val outline = ensureOutlineDrawable(view)
    if (outlineStyle != null) {
      outline.outlineStyle = outlineStyle
    }
  }

  public fun getOutlineStyle(view: View): OutlineStyle? = getOutlineDrawable(view)?.outlineStyle

  @JvmStatic
  public fun setOutlineWidth(view: View, width: Float): Unit {
    if (ViewUtil.getUIManagerType(view) != UIManagerType.FABRIC) {
      return
    }

    val outline = ensureOutlineDrawable(view)
    outline.outlineWidth = width.dpToPx()
  }

  public fun getOutlineWidth(view: View): Float? = getOutlineDrawable(view)?.outlineOffset

  @JvmStatic
  public fun setBoxShadow(view: View, shadows: List<BoxShadow>): Unit {
    if (ViewUtil.getUIManagerType(view) != UIManagerType.FABRIC) {
      return
    }

    val outerShadows = mutableListOf<OutsetBoxShadowDrawable>()
    val innerShadows = mutableListOf<InsetBoxShadowDrawable>()

    val compositeBackgroundDrawable = ensureCompositeBackgroundDrawable(view)
    val borderInsets = compositeBackgroundDrawable.borderInsets
    val borderRadius = compositeBackgroundDrawable.borderRadius

    for (boxShadow in shadows) {
      val offsetX = boxShadow.offsetX
      val offsetY = boxShadow.offsetY
      val color = boxShadow.color ?: Color.BLACK
      val blurRadius = boxShadow.blurRadius ?: 0f
      val spreadDistance = boxShadow.spreadDistance ?: 0f
      val inset = boxShadow.inset ?: false

      if (inset && Build.VERSION.SDK_INT >= MIN_INSET_BOX_SHADOW_SDK_VERSION) {
        innerShadows.add(
            InsetBoxShadowDrawable(
                context = view.context,
                borderRadius = borderRadius,
                borderInsets = borderInsets,
                shadowColor = color,
                offsetX = offsetX,
                offsetY = offsetY,
                blurRadius = blurRadius,
                spread = spreadDistance))
      } else if (!inset && Build.VERSION.SDK_INT >= MIN_OUTSET_BOX_SHADOW_SDK_VERSION) {
        outerShadows.add(
            OutsetBoxShadowDrawable(
                context = view.context,
                borderRadius = borderRadius,
                shadowColor = color,
                offsetX = offsetX,
                offsetY = offsetY,
                blurRadius = blurRadius,
                spread = spreadDistance))
      }
    }

    view.background =
        ensureCompositeBackgroundDrawable(view)
            .withNewShadows(outerShadows = outerShadows, innerShadows = innerShadows)
  }

  @JvmStatic
  public fun setBoxShadow(view: View, shadows: ReadableArray?): Unit {
    if (shadows == null) {
      BackgroundStyleApplicator.setBoxShadow(view, emptyList())
      return
    }

    val shadowStyles = mutableListOf<BoxShadow>()
    for (i in 0..<shadows.size()) {
      shadowStyles.add(checkNotNull(BoxShadow.parse(shadows.getMap(i), view.context)))
    }
    BackgroundStyleApplicator.setBoxShadow(view, shadowStyles)
  }

  @JvmStatic
  public fun setFeedbackUnderlay(view: View, drawable: Drawable?): Unit {
    view.background = ensureCompositeBackgroundDrawable(view).withNewFeedbackUnderlay(drawable)
  }

  @JvmStatic
  public fun clipToPaddingBox(view: View, canvas: Canvas): Unit {
    // The canvas may be scrolled, so we need to offset
    val drawingRect = Rect()
    view.getDrawingRect(drawingRect)

    val cssBackground = getCSSBackground(view)
    if (cssBackground == null) {
      canvas.clipRect(drawingRect)
      return
    }

    val paddingBoxPath = cssBackground.paddingBoxPath
    if (paddingBoxPath != null) {
      paddingBoxPath.offset(drawingRect.left.toFloat(), drawingRect.top.toFloat())
      canvas.clipPath(paddingBoxPath)
    } else {
      val paddingBoxRect = cssBackground.paddingBoxRect
      paddingBoxRect.offset(drawingRect.left.toFloat(), drawingRect.top.toFloat())
      canvas.clipRect(paddingBoxRect)
    }
  }

  @JvmStatic
  public fun reset(view: View): Unit {
    if (view.background is CompositeBackgroundDrawable) {
      view.background = (view.background as CompositeBackgroundDrawable).originalBackground
    }
  }

  private fun ensureCompositeBackgroundDrawable(view: View): CompositeBackgroundDrawable {
    if (view.background is CompositeBackgroundDrawable) {
      return view.background as CompositeBackgroundDrawable
    }

    val compositeDrawable = CompositeBackgroundDrawable(originalBackground = view.background)
    view.background = compositeDrawable
    return compositeDrawable
  }

  private fun getCompositeBackgroundDrawable(view: View): CompositeBackgroundDrawable? =
      view.background as? CompositeBackgroundDrawable

  private fun ensureCSSBackground(view: View): CSSBackgroundDrawable {
    val compositeBackgroundDrawable = ensureCompositeBackgroundDrawable(view)
    if (compositeBackgroundDrawable.cssBackground != null) {
      return compositeBackgroundDrawable.cssBackground
    } else {
      val cssBackground = CSSBackgroundDrawable(view.context)
      view.background = compositeBackgroundDrawable.withNewCssBackground(cssBackground)
      return cssBackground
    }
  }

  private fun getCSSBackground(view: View): CSSBackgroundDrawable? =
      getCompositeBackgroundDrawable(view)?.cssBackground

  private fun ensureOutlineDrawable(view: View): OutlineDrawable {
    val compositeBackgroundDrawable = ensureCompositeBackgroundDrawable(view)
    var outline = compositeBackgroundDrawable.outline
    if (outline == null) {
      outline =
          OutlineDrawable(
              context = view.context,
              borderRadius = compositeBackgroundDrawable.borderRadius,
              outlineColor = Color.BLACK,
              outlineOffset = 0f,
              outlineStyle = OutlineStyle.SOLID,
              outlineWidth = 0f,
          )
      view.background = compositeBackgroundDrawable.withNewOutline(outline)
    }

    return outline
  }

  private fun getOutlineDrawable(view: View): OutlineDrawable? =
      getCompositeBackgroundDrawable(view)?.outline
}
