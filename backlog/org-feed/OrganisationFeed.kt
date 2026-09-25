// Org Feed tab from the Pair screen (Pair / Org Feed toggle), removed from the app on 2026-09-25.
// Sample organisation QR channels; not wired to anything. See backlog/README.md.

// -----------------------------------------------------------------------------
// Organisation Feed
// -----------------------------------------------------------------------------

@Composable
private fun OrganisationFeedContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {

        Text(
            text = "ORGANISATION QR FEED",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = SecondaryText
        )

        Spacer(modifier = Modifier.height(12.dp))

        OrganisationCard(
            name = "NDRF Unit 07",
            description = "Emergency response channel"
        )

        Spacer(modifier = Modifier.height(10.dp))

        OrganisationCard(
            name = "Relief Camp Alpha",
            description = "Team communication channel"
        )

        Spacer(modifier = Modifier.height(10.dp))

        OrganisationCard(
            name = "Field Operations",
            description = "Local emergency network"
        )
    }
}

@Composable
private fun OrganisationCard(
    name: String,
    description: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                GrayBorder,
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftLightGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "QR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DeepDarkGreen
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = SecondaryText
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
