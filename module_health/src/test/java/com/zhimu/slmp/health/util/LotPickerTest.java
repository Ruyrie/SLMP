package com.zhimu.slmp.health.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zhimu.slmp.health.TestRules;
import com.zhimu.slmp.health.model.vo.DrugLotOptionVO;
import com.zhimu.slmp.health.model.vo.DrugLotOptionsVO;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

/**
 * 选择疫苗批号（图 8-5）：默认先到期先出的 2025081；已过期 2025042、已停用 2025097 不可选；结存不足、临期提示；员工不显示单价。
 */
public class LotPickerTest {

    private static final String OPTIONS = "{'default_lot_id':6001,"
            + "'selectable':[{'lot_id':6001,'lot_no':'2025081','expire_date':'2026-12-30','lot_status':'NORMAL',"
            + "'days_to_expire':72,'stock':400,'avg_price':2.35,'priority_out':true},"
            + "{'lot_id':6002,'lot_no':'2025113','expire_date':'2027-03-15','lot_status':'NEAR_EXPIRY',"
            + "'days_to_expire':20,'stock':150,'avg_price':2.4}],"
            + "'unselectable':[{'lot_id':6003,'lot_no':'2025042','expire_date':'2026-10-12','lot_status':'EXPIRED'},"
            + "{'lot_id':6004,'lot_no':'2025097','expire_date':'2027-01-20','lot_status':'RECALLED'}]}";

    private final DrugLotOptionsVO options = TestRules.parse(OPTIONS, DrugLotOptionsVO.class);

    @Test
    public void defaultLotIsEarliestExpiry() {
        DrugLotOptionVO lot = LotPicker.defaultLot(options);
        assertEquals("2025081", lot.getLotNo());
        assertTrue(LotPicker.isDefault(options, lot));
        assertEquals("有效期至 2026-12-30 · 已默认选择有效期最早的批号", LotPicker.helperText(lot, true));
    }

    @Test
    public void expiredAndRecalledLotsCannotBeSelected() {
        assertFalse(LotPicker.isSelectable(options, 6003));
        assertFalse(LotPicker.isSelectable(options, 6004));
        assertNull(LotPicker.find(options, 6003L));
        assertTrue(LotPicker.isSelectable(options, 6002));
    }

    @Test
    public void insufficientStockText() {
        DrugLotOptionVO lot = LotPicker.find(options, 6002L);
        BigDecimal need = new BigDecimal("200");
        assertTrue(LotPicker.isInsufficient(lot, need));
        assertEquals("批号 2025113 结存 150 头份，不够本次 200 头份",
                TestRules.plain(LotPicker.insufficientText(lot, need, "头份")));
        assertFalse(LotPicker.isInsufficient(LotPicker.defaultLot(options), need));
    }

    @Test
    public void noLotTextByRole() {
        assertEquals("猪瘟活疫苗没有可用的库存批号。请联系所有者或场长先登记入库。", LotPicker.noLotText("猪瘟活疫苗", true));
        assertEquals("猪瘟活疫苗没有可用的库存批号。请先登记入库。", LotPicker.noLotText("猪瘟活疫苗", false));
    }

    @Test
    public void sheetRowsTagsAndPrice() {
        List<LotPicker.Row> owner = LotPicker.rows(options, "头份", true, new BigDecimal("200"));
        assertEquals(4, owner.size());
        assertEquals("推荐", owner.get(0).tagText);
        assertTrue(TestRules.plain(owner.get(0).hint).endsWith("单价 2.35 元 / 头份"));
        assertEquals("结存不足", owner.get(1).tagText);
        assertTrue(TestRules.plain(owner.get(1).hint).endsWith("单价 2.40 元 / 头份"));
        assertEquals("已过期", owner.get(2).tagText);
        assertTrue(owner.get(2).disabled);
        assertEquals("有效期至 2026-10-12 · 只能报废", owner.get(2).hint);
        assertEquals("已停用", owner.get(3).tagText);
        assertEquals("平台已停用该批号 · 只能报废", owner.get(3).hint);
        // 员工不渲染单价；不传本次用量时临期批号标“临期”
        List<LotPicker.Row> staff = LotPicker.rows(options, "头份", false, null);
        assertFalse(staff.get(0).hint.contains("单价"));
        assertEquals("临期", staff.get(1).tagText);
    }
}
