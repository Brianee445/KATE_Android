// app/src/main/java/com/dti/kate/ui/components/KateAvatar.kt

@Composable
fun KateAvatar(
    state: KateAvatarState = KateAvatarState.IDLE,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    val avatarRes = when (state) {
        KateAvatarState.IDLE -> R.drawable.kate_avatar_idle
        KateAvatarState.LISTENING -> R.drawable.kate_avatar_listening
        KateAvatarState.THINKING -> R.drawable.kate_avatar_thinking
        KateAvatarState.SPEAKING -> R.drawable.kate_avatar_speaking
        KateAvatarState.SLEEPING -> R.drawable.kate_avatar_sleeping
        KateAvatarState.PRODUCTIVE -> R.drawable.kate_avatar_productive
        KateAvatarState.ERROR -> R.drawable.kate_avatar_error
    }
    
    Image(
        painter = painterResource(avatarRes),
        contentDescription = "Kate Avatar - $state",
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(
                width = 2.dp,
                color = when (state) {
                    KateAvatarState.LISTENING -> KateListening
                    KateAvatarState.THINKING -> Purple70
                    KateAvatarState.SPEAKING -> KateSpeaking
                    KateAvatarState.ERROR -> Error
                    else -> Color.Transparent
                },
                shape = CircleShape
            ),
        contentScale = ContentScale.Crop,
    )
}
