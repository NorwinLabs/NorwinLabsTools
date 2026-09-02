package com.norwinlabs.tools

data class DataCenter(
    val name: String,
    val operator: String,
    val location: String,
    val lat: Double,
    val lng: Double
)

/**
 * A curated sample of large, publicly documented hyperscale data center campuses, drawn from
 * company announcements and public reporting. Coordinates are approximate (campus/city level),
 * not exact facility addresses.
 */
object DataCenters {
    val ALL = listOf(
        DataCenter("AWS US East (N. Virginia)", "Amazon Web Services", "Ashburn, Virginia, USA", 39.0438, -77.4874),
        DataCenter("Microsoft Azure Quincy", "Microsoft", "Quincy, Washington, USA", 47.2343, -119.8524),
        DataCenter("Microsoft Boydton Campus", "Microsoft", "Boydton, Virginia, USA", 36.6676, -78.3894),
        DataCenter("Google The Dalles", "Google", "The Dalles, Oregon, USA", 45.5946, -121.1787),
        DataCenter("Google Council Bluffs", "Google", "Council Bluffs, Iowa, USA", 41.2619, -95.8608),
        DataCenter("Google Mayes County", "Google", "Pryor, Oklahoma, USA", 36.3212, -95.3159),
        DataCenter("Meta Prineville", "Meta", "Prineville, Oregon, USA", 44.2999, -120.8317),
        DataCenter("Meta Los Lunas", "Meta", "Los Lunas, New Mexico, USA", 34.7734, -106.7415),
        DataCenter("Meta Papillion", "Meta", "Papillion, Nebraska, USA", 41.1544, -96.0422),
        DataCenter("Switch Tahoe Reno", "Switch", "Tahoe Reno Industrial Center, Nevada, USA", 39.5296, -119.4457),
        DataCenter("Stargate Abilene", "OpenAI / Oracle / SoftBank", "Abilene, Texas, USA", 32.4487, -99.7331),
        DataCenter("Google Hamina", "Google", "Hamina, Finland", 60.5693, 27.1978),
        DataCenter("Google Eemshaven", "Google", "Eemshaven, Netherlands", 53.4383, 6.8355),
        DataCenter("Google St. Ghislain", "Google", "Saint-Ghislain, Belgium", 50.4542, 3.8188),
        DataCenter("Microsoft Dublin", "Microsoft", "Dublin, Ireland", 53.3498, -6.2603),
        DataCenter("Meta Clonee", "Meta", "Clonee, Ireland", 53.4064, -6.4842),
        DataCenter("AWS Dublin", "Amazon Web Services", "Dublin, Ireland", 53.4239, -6.2380),
        DataCenter("China Telecom Inner Mongolia Hub", "China Telecom", "Hohhot, Inner Mongolia, China", 40.8414, 111.7519),
        DataCenter("Range International Information Hub", "Range Technology", "Langfang, Hebei, China", 39.5196, 116.7069),
        DataCenter("Alibaba Cloud Hangzhou", "Alibaba Cloud", "Hangzhou, Zhejiang, China", 30.2741, 120.1551),
        DataCenter("Tencent Guiyang", "Tencent", "Guiyang, Guizhou, China", 26.5783, 106.7135),
        DataCenter("Google Jurong West", "Google", "Singapore", 1.3496, 103.7063),
        DataCenter("Microsoft São Paulo", "Microsoft", "São Paulo, Brazil", -23.5505, -46.6333)
    )
}
