package com.returnright.seed;

import java.util.List;

/** Hand-curated catalogue (Part 2 §16.1): 40 products in 6 categories, fictional brands, prices in rupees. */
final class Catalogue {

    record Cat(short id, String code, String name, boolean returnable, Integer windowDays) {}

    record Item(String sku, String name, String brand, String category, long rupees) {}

    static final List<Cat> CATEGORIES = List.of(
            new Cat((short) 1, "ELECTRONICS", "Electronics", true, 10),
            new Cat((short) 2, "APPAREL", "Apparel", true, 30),
            new Cat((short) 3, "FOOTWEAR", "Footwear", true, 30),
            new Cat((short) 4, "HOME_KITCHEN", "Home & Kitchen", true, 30),
            new Cat((short) 5, "INNERWEAR", "Innerwear", false, null),
            new Cat((short) 6, "PERISHABLES", "Perishables", false, null));

    static final List<Item> PRODUCTS = List.of(
            // Electronics (8)
            new Item("EL-LAP-14", "Aero 14\" Laptop, 16 GB / 512 GB", "Voltix", "ELECTRONICS", 62_999),
            new Item("EL-PHN-X5", "Nova X5 Smartphone, 128 GB", "Voltix", "ELECTRONICS", 34_999),
            new Item("EL-TAB-11", "Slate 11 Tablet", "Voltix", "ELECTRONICS", 24_999),
            new Item("EL-WCH-S2", "Pulse S2 Smartwatch", "Auralis", "ELECTRONICS", 12_499),
            new Item("EL-SPK-BT", "Boom Mini Bluetooth Speaker", "Auralis", "ELECTRONICS", 3_999),
            new Item("EL-EAR-PR", "AirTone Pro Wireless Earbuds", "Auralis", "ELECTRONICS", 2_499),
            new Item("EL-PWR-20", "20,000 mAh Power Bank", "Chargely", "ELECTRONICS", 1_799),
            new Item("EL-KBD-MX", "Mechanical Keyboard, Hot-swap", "Keyforge", "ELECTRONICS", 5_499),
            // Apparel (10)
            new Item("AP-JNS-SLM", "Slim-fit Stretch Jeans", "Indigo Lane", "APPAREL", 1_999),
            new Item("AP-KRT-CTN", "Cotton Straight Kurta", "Rangrez", "APPAREL", 1_499),
            new Item("AP-JKT-DNM", "Denim Trucker Jacket", "Indigo Lane", "APPAREL", 3_499),
            new Item("AP-TSH-PK3", "Crew-neck T-shirt (Pack of 3)", "Basics Co.", "APPAREL", 999),
            new Item("AP-SAR-SLK", "Banarasi Silk Saree", "Rangrez", "APPAREL", 6_999),
            new Item("AP-DRS-MID", "Floral Midi Dress", "Saffron Street", "APPAREL", 2_299),
            new Item("AP-SHR-LIN", "Linen Shirt", "Basics Co.", "APPAREL", 1_799),
            new Item("AP-HDY-FLC", "Fleece Hoodie", "Northwind", "APPAREL", 2_499),
            new Item("AP-TRK-JOG", "Track Joggers", "Northwind", "APPAREL", 1_299),
            new Item("AP-BLZ-WOL", "Wool-blend Blazer", "Saffron Street", "APPAREL", 5_999),
            // Footwear (6)
            new Item("FW-RUN-PRO", "Stride Pro Running Shoes", "Fleetfoot", "FOOTWEAR", 11_999),
            new Item("FW-SNK-WHT", "Classic White Sneakers", "Fleetfoot", "FOOTWEAR", 3_499),
            new Item("FW-SND-LTH", "Leather Sandals", "Mochi Works", "FOOTWEAR", 1_999),
            new Item("FW-BOT-CHL", "Chelsea Boots", "Mochi Works", "FOOTWEAR", 4_999),
            new Item("FW-SLP-CMF", "Comfort Slippers", "Basics Co.", "FOOTWEAR", 499),
            new Item("FW-FRM-OXF", "Formal Oxford Shoes", "Mochi Works", "FOOTWEAR", 3_999),
            // Home & Kitchen (8)
            new Item("HK-MIX-750", "750 W Mixer Grinder", "KitchenKraft", "HOME_KITCHEN", 3_999),
            new Item("HK-AFR-4L", "4 L Digital Air Fryer", "KitchenKraft", "HOME_KITCHEN", 8_999),
            new Item("HK-SOF-CVR", "Stretch Sofa Cover, 3-seater", "Homely", "HOME_KITCHEN", 1_299),
            new Item("HK-BED-KNG", "Cotton Bedsheet Set, King", "Homely", "HOME_KITCHEN", 1_999),
            new Item("HK-CKW-SET", "Tri-ply Cookware Set (5 pc)", "KitchenKraft", "HOME_KITCHEN", 6_499),
            new Item("HK-VAC-RBT", "Robot Vacuum Cleaner", "Homely", "HOME_KITCHEN", 24_999),
            new Item("HK-LMP-LED", "LED Desk Lamp", "Lumio", "HOME_KITCHEN", 1_499),
            new Item("HK-BTL-STL", "Insulated Steel Bottle", "Lumio", "HOME_KITCHEN", 699),
            // Innerwear (4)
            new Item("IW-BRF-PK5", "Cotton Briefs (Pack of 5)", "Basics Co.", "INNERWEAR", 799),
            new Item("IW-VST-PK3", "Cotton Vests (Pack of 3)", "Basics Co.", "INNERWEAR", 599),
            new Item("IW-BRA-T2", "T-shirt Bra (Pack of 2)", "Saffron Street", "INNERWEAR", 1_499),
            new Item("IW-THM-SET", "Thermal Set", "Northwind", "INNERWEAR", 1_299),
            // Perishables (4)
            new Item("PR-DRY-1KG", "Premium Dry Fruits Box, 1 kg", "Harvest Box", "PERISHABLES", 1_999),
            new Item("PR-CHC-GFT", "Artisan Chocolate Gift Box", "Cocoa Ghar", "PERISHABLES", 899),
            new Item("PR-TEA-ASM", "Assam Tea, 500 g", "Chai Estate", "PERISHABLES", 449),
            new Item("PR-HNY-RAW", "Raw Forest Honey, 500 g", "Harvest Box", "PERISHABLES", 399));

    static final List<String[]> CITIES = List.of(
            new String[] {"Indore", "Madhya Pradesh", "452"}, new String[] {"Raipur", "Chhattisgarh", "492"},
            new String[] {"Bengaluru", "Karnataka", "560"}, new String[] {"Mumbai", "Maharashtra", "400"},
            new String[] {"Pune", "Maharashtra", "411"}, new String[] {"New Delhi", "Delhi", "110"},
            new String[] {"Hyderabad", "Telangana", "500"}, new String[] {"Chennai", "Tamil Nadu", "600"},
            new String[] {"Kolkata", "West Bengal", "700"}, new String[] {"Jaipur", "Rajasthan", "302"},
            new String[] {"Ahmedabad", "Gujarat", "380"}, new String[] {"Lucknow", "Uttar Pradesh", "226"},
            new String[] {"Bhopal", "Madhya Pradesh", "462"}, new String[] {"Kochi", "Kerala", "682"});

    private Catalogue() {}
}
