package martinlobato.com.ar.clima.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import martinlobato.com.ar.clima.databinding.ItemDataCellBinding;

/** Grilla de celdas "LABEL / valor" de dos columnas (mismo patrón visual que la de MyLoc). */
final class CellGrid {

    private CellGrid() {
    }

    /**
     * Agrega una fila de dos celdas a {@code grid} y las devuelve, para poder sumarles su mini
     * gráfico ({@code gauge}).
     */
    static ItemDataCellBinding[] addRow(LinearLayout grid, int label1, String value1, int label2, String value2) {
        LayoutInflater inflater = LayoutInflater.from(grid.getContext());
        float density = grid.getResources().getDisplayMetrics().density;
        LinearLayout row = new LinearLayout(grid.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (grid.getChildCount() > 0) {
            rowParams.topMargin = Math.round(10 * density);
        }
        row.setLayoutParams(rowParams);
        int gap = Math.round(5 * density);
        ItemDataCellBinding a = addCell(inflater, row, label1, value1, 0, gap);
        ItemDataCellBinding b = addCell(inflater, row, label2, value2, gap, 0);
        grid.addView(row);
        return new ItemDataCellBinding[]{a, b};
    }

    private static ItemDataCellBinding addCell(LayoutInflater inflater, LinearLayout row, int labelRes, String value,
                                int marginStart, int marginEnd) {
        ItemDataCellBinding cell = ItemDataCellBinding.inflate(inflater, row, false);
        cell.tvLabel.setText(labelRes);
        cell.tvValue.setText(value);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cell.getRoot().getLayoutParams();
        lp.setMarginStart(marginStart);
        lp.setMarginEnd(marginEnd);
        row.addView(cell.getRoot());
        return cell;
    }
}
