package com.miara.cuentame.feature.menu

import com.miara.cuentame.core.common.ids.MenuId
import com.miara.cuentame.core.presentation.navigation.AppRoutes
import com.miara.cuentame.core.presentation.navigation.Destination
import com.miara.cuentame.core.presentation.navigation.RouteEncoder
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuCatalogNavigationTest {
    private lateinit var original: RouteEncoder
    @Before fun setUp() { original=AppRoutes.encoder;AppRoutes.encoder=object:RouteEncoder{override fun encode(s:String)=s} }
    @After fun tearDown() { AppRoutes.encoder=original }
    @Test fun `catalog routes remain distinct from menu item routes`() {
        assertEquals("menus", Destination.MENU_CATALOG_LIST.route)
        assertEquals("menus/menu-1", AppRoutes.menuCatalogDetail(MenuId("menu-1")))
        assertEquals("menu-items", Destination.MENU_RECIPE_LIST.route)
    }
}
