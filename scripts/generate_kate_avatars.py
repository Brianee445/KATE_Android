#!/usr/bin/env python3
"""
Kate Avatar Generator - One Source to Rule Them All
====================================================
Place your source avatar at: app/src/main/res/drawable/kate_avatar_source.png
Run this script: python scripts/generate_kate_avatars.py

It will generate:
- All density variants (MDPI → XXXHDPI)
- All states (idle, listening, thinking, speaking, sleeping, productive, error)
- Proper color overlays for each state
- App launcher icons
"""

import os
import sys
import shutil
from pathlib import Path
from PIL import Image, ImageFilter, ImageEnhance, ImageDraw, ImageChops

# ==================== CONFIGURATION ====================

# Avatar states with their colors and effects
AVATAR_STATES = {
    "idle": {
        "color": (124, 58, 237, 255),    # #7C3AED - Purple
        "effect": "glow",
        "glow_color": (124, 58, 237, 120),
        "description": "Default - waiting"
    },
    "listening": {
        "color": (212, 255, 79, 255),     # #D4FF4F - Green/Yellow
        "effect": "pulse",
        "glow_color": (212, 255, 79, 180),
        "description": "Listening - green glow"
    },
    "thinking": {
        "color": (124, 58, 237, 255),     # #7C3AED - Purple
        "effect": "pulse_fast",
        "glow_color": (124, 58, 237, 200),
        "description": "Processing - fast pulse"
    },
    "speaking": {
        "color": (255, 107, 157, 255),    # #FF6B9D - Pink
        "effect": "wave",
        "glow_color": (255, 107, 157, 180),
        "description": "Speaking - pink glow"
    },
    "sleeping": {
        "color": (167, 154, 184, 255),    # #A79AB8 - Muted
        "effect": "dim",
        "glow_color": (167, 154, 184, 60),
        "description": "Sleeping - dimmed"
    },
    "productive": {
        "color": (76, 175, 80, 255),      # #4CAF50 - Green
        "effect": "glow",
        "glow_color": (76, 175, 80, 160),
        "description": "Productivity mode"
    },
    "error": {
        "color": (255, 84, 112, 255),     # #FF5470 - Red
        "effect": "shake",
        "glow_color": (255, 84, 112, 180),
        "description": "Error state"
    }
}

# Density configurations (DPI buckets)
DENSITIES = {
    "drawable-xxxhdpi": 192,   # 4.0x - Extra Extra Extra High
    "drawable-xxhdpi": 144,    # 3.0x - Extra Extra High (BASE)
    "drawable-xhdpi": 96,      # 2.0x - Extra High
    "drawable-hdpi": 72,       # 1.5x - High
    "drawable-mdpi": 48,       # 1.0x - Medium
    "drawable-ldpi": 36,       # 0.75x - Low (optional)
}

# App icon sizes
ICON_SIZES = {
    "mipmap-xxxhdpi": 192,
    "mipmap-xxhdpi": 144,
    "mipmap-xhdpi": 96,
    "mipmap-hdpi": 72,
    "mipmap-mdpi": 48,
}

# ==================== IMAGE PROCESSING FUNCTIONS ====================

def apply_color_overlay(image, color):
    """Apply a semi-transparent color overlay"""
    if image.mode != 'RGBA':
        image = image.convert('RGBA')
    
    overlay = Image.new('RGBA', image.size, color)
    # Blend with 35% opacity for the overlay
    return Image.blend(image, overlay, 0.35)

def apply_glow(image, glow_color, radius=12, intensity=1.4):
    """Apply a glow effect around the avatar"""
    if image.mode != 'RGBA':
        image = image.convert('RGBA')
    
    # Create glow layer from alpha channel
    alpha = image.split()[-1]
    glow = Image.new('RGBA', image.size, (0, 0, 0, 0))
    glow.putalpha(alpha)
    
    # Color the glow
    glow = apply_color_overlay(glow, glow_color)
    
    # Blur for glow effect
    glow = glow.filter(ImageFilter.GaussianBlur(radius=radius))
    
    # Enhance brightness
    enhancer = ImageEnhance.Brightness(glow)
    glow = enhancer.enhance(intensity)
    
    # Composite glow with original
    result = Image.alpha_composite(glow, image)
    return result

def apply_dim_effect(image, brightness=0.35):
    """Dim the image for sleeping state"""
    enhancer = ImageEnhance.Brightness(image)
    return enhancer.enhance(brightness)

def apply_shake_effect(image):
    """Add subtle red distortion for error state"""
    # Just add a red glow overlay for simplicity
    return apply_glow(image, (255, 50, 50, 150), radius=15, intensity=1.6)

def add_pulse_ring(image, color):
    """Add a subtle pulse ring around the avatar"""
    result = image.copy()
    draw = ImageDraw.Draw(result)
    size = result.size[0]
    
    # Draw inner glow ring
    ring_size = size - 8
    draw.ellipse(
        [4, 4, ring_size, ring_size],
        outline=(color[0], color[1], color[2], 80),
        width=3
    )
    
    # Draw outer ring
    ring_size = size - 14
    draw.ellipse(
        [7, 7, ring_size, ring_size],
        outline=(color[0], color[1], color[2], 40),
        width=2
    )
    
    return result

def generate_avatar_state(source_img, state, config, output_dir):
    """Generate all variants for a single avatar state"""
    try:
        # Start with source
        img = source_img.copy()
        
        # Apply state-specific effects
        effect = config["effect"]
        color = config["color"]
        glow_color = config["glow_color"]
        
        if effect == "glow":
            img = apply_glow(img, glow_color)
        elif effect == "pulse" or effect == "pulse_fast":
            img = apply_glow(img, glow_color, radius=14, intensity=1.2)
            img = add_pulse_ring(img, color)
        elif effect == "wave":
            img = apply_glow(img, glow_color, radius=18, intensity=1.6)
        elif effect == "dim":
            img = apply_dim_effect(img, 0.35)
        elif effect == "shake":
            img = apply_shake_effect(img)
        
        # Apply color overlay (subtle tint)
        img = apply_color_overlay(img, color)
        
        # Generate all densities
        for density_name, size in DENSITIES.items():
            resized = img.resize((size, size), Image.Resampling.LANCZOS)
            
            # Create directory
            density_dir = Path(output_dir) / density_name
            density_dir.mkdir(parents=True, exist_ok=True)
            
            # Save
            filename = f"kate_avatar_{state}.png"
            filepath = density_dir / filename
            resized.save(filepath, "PNG", optimize=True)
            
        return True
        
    except Exception as e:
        print(f"  ❌ Failed: {e}")
        return False

def make_square_icon(source_img, size, bg_color=(124, 58, 237, 255)):
    """Compose the launcher PNG: purple background + inset, centered avatar.
    Mirrors the adaptive-icon layout (avatar at ~64% of canvas) so the
    legacy PNG fallback matches what the adaptive icon looks like."""
    canvas = Image.new('RGBA', (size, size), bg_color)
    logo_size = int(size * 0.64)
    logo = source_img.resize((logo_size, logo_size), Image.Resampling.LANCZOS)
    offset = ((size - logo_size) // 2, (size - logo_size) // 2)
    canvas.paste(logo, offset, logo)
    return canvas

def make_round_icon(square_img, size):
    """Mask a square icon into a circle for the ic_launcher_round variant."""
    mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size, size), fill=255)
    round_icon = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    round_icon.paste(square_img, (0, 0), mask)
    return round_icon

def generate_app_icons(source_img, output_dir):
    """Generate legacy (pre-API-26) launcher icons for every density.
    These are the fallback used on API 24-25 and act as a safety net
    if adaptive icon resolution ever fails on a given device/launcher."""
    try:
        for density_name, size in ICON_SIZES.items():
            density_dir = Path(output_dir) / density_name
            density_dir.mkdir(parents=True, exist_ok=True)

            square = make_square_icon(source_img, size)
            round_ = make_round_icon(square, size)

            square.save(density_dir / "ic_launcher.png", "PNG", optimize=True)
            round_.save(density_dir / "ic_launcher_round.png", "PNG", optimize=True)

        return True
    except Exception as e:
        print(f"  ❌ Failed to generate icons: {e}")
        return False

def generate_adaptive_icon(output_dir):
    """Generate adaptive icon XML (Android 8+).

    IMPORTANT: the root element for a launcher mipmap XML resource on
    API 26+ MUST be <adaptive-icon> with <background> and <foreground>
    children. A bare <layer-list> is NOT a valid adaptive icon and will
    fail to resolve at runtime on API 26+, causing the OS to silently
    fall back to the default Android robot icon. Do not "simplify" this
    to a layer-list.
    """
    adaptive_dir = Path(output_dir) / "mipmap-anydpi-v26"
    adaptive_dir.mkdir(parents=True, exist_ok=True)

    adaptive_icon_xml = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>'''

    with open(adaptive_dir / "ic_launcher.xml", "w") as f:
        f.write(adaptive_icon_xml)
    with open(adaptive_dir / "ic_launcher_round.xml", "w") as f:
        f.write(adaptive_icon_xml)

    # Foreground: the actual avatar bitmap, inset so it isn't clipped by
    # circular/squircle/rounded-square launcher masks (Android's spec
    # wants only the center ~66dp of the 108dp canvas holding real content).
    drawable_dir = Path(output_dir) / "drawable"
    drawable_dir.mkdir(parents=True, exist_ok=True)

    foreground_xml = '''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:insetLeft="18%"
    android:insetTop="18%"
    android:insetRight="18%"
    android:insetBottom="18%">
    <bitmap
        android:src="@drawable/kate_avatar_source"
        android:gravity="center" />
</inset>'''

    with open(drawable_dir / "ic_launcher_foreground.xml", "w") as f:
        f.write(foreground_xml)

    # Background (purple)
    background_xml = '''<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#7C3AED" />
</shape>'''

    with open(drawable_dir / "ic_launcher_background.xml", "w") as f:
        f.write(background_xml)

# ==================== MAIN ====================

def main():
    print("=" * 55)
    print("  🎨 KATE AVATAR GENERATOR - One Source to Rule Them All")
    print("=" * 55)
    
    # Find project root
    script_dir = Path(__file__).parent.absolute()
    project_root = script_dir.parent
    source_dir = project_root / "app" / "src" / "main" / "res" / "drawable"
    output_dir = project_root / "app" / "src" / "main" / "res"
    
    # Source avatar path
    source_path = source_dir / "kate_avatar_source.png"
    
    # Check source exists
    if not source_path.exists():
        print(f"\n❌ Source file not found!")
        print(f"   Expected: {source_path}")
        print(f"\n📝 Please create your avatar (144x144px PNG) at:")
        print(f"   {source_dir}")
        print(f"   Name it: kate_avatar_source.png")
        sys.exit(1)
    
    print(f"\n📁 Source: {source_path}")
    print(f"📁 Output: {output_dir}\n")
    
    # Load source image
    try:
        source_img = Image.open(source_path).convert('RGBA')
        print(f"✅ Loaded: {source_img.size[0]}x{source_img.size[1]}px")
    except Exception as e:
        print(f"❌ Failed to load source: {e}")
        sys.exit(1)
    
    # ============ GENERATE AVATARS ============
    print("\n🔄 Generating avatar states...\n")
    
    success_count = 0
    for state, config in AVATAR_STATES.items():
        print(f"  → {state} ({config['description']})")
        if generate_avatar_state(source_img, state, config, output_dir):
            success_count += 1
            print(f"    ✅ Generated all densities")
    
    print(f"\n✅ Generated {success_count}/{len(AVATAR_STATES)} avatar states")
    
    # ============ GENERATE APP ICONS ============
    print("\n🎯 Generating app launcher icons...")
    
    if generate_app_icons(source_img, output_dir):
        print("  ✅ App icons generated")
    
    # ============ GENERATE ADAPTIVE ICON ============
    print("\n📱 Generating adaptive icon (Android 8+)...")
    
    generate_adaptive_icon(output_dir)
    print("  ✅ Adaptive icon generated")
    
    # ============ SUMMARY ============
    print("\n" + "=" * 55)
    print("✅ ALL DONE! Avatars are ready.")
    print("=" * 55)
    print("\n📁 Generated files:")
    
    # List generated directories
    for density in DENSITIES.keys():
        density_path = output_dir / density
        if density_path.exists():
            count = len(list(density_path.glob("kate_avatar_*.png")))
            print(f"   📂 {density}: {count} avatar files")
    
    print(f"\n📂 mipmap-*/: App icons")
    print(f"📂 mipmap-anydpi-v26/: Adaptive icons")
    print(f"📂 drawable/: Vector assets")
    
    print("\n" + "=" * 55)
    print("  🚀 Next: Build and test!")
    print("  ./gradlew assembleDebug")
    print("=" * 55)

if __name__ == "__main__":
    main()
