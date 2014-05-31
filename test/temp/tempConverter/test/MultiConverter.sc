class MultiConverter extends AppFrame {
   static class Converter {
      double value1, value2;
      String unit1, unit2;
      String title;
      public String toString() {
         return title;
      }
   }
   
   object converters extends sc.util.ComponentList<Converter> {
      object temperature extends Converter {
         value1 = 0;
         value2 :=: value1 * 9.0 / 5.0 + 32; 
         unit1 = "Celcius";
         unit2 = "Farenheit";
         title = "Temperature";
      }
   }

   // Combo box to choose the current conversion algorithm
   object converterChoice extends JComboBox {
      int alignx = 20;
      items = converters;
      location := SwingUtil.point(xpad+alignx, ypad);
      size := SwingUtil.dimension(windowWidth - 2*(xpad+alignx), preferredSize.height);
   }

   // currentConverter will always point to the selected item in the combo box
   Converter currentConverter := converters.get(converterChoice.selectedIndex);

   // first row - label + field follows current converter
   int row1y := (int)(ypad + converterChoice.location.y + converterChoice.size.height);

   object unit1Label extends JLabel {
      text := currentConverter.unit1;  // Display's converter's "unit 1"
      labelFor = unit1Field;
      location := SwingUtil.point(xpad, row1y + baseline);
      size := preferredSize;
   }
   object unit1Field extends JTextField {
      location := SwingUtil.point(xpad + unit1Label.preferredSize.width + gap, row1y);
      size := SwingUtil.dimension(windowWidth - unit1Label.size.width - xpad - 2*gap, preferredSize.height);
      // Bind's text property to current converter's value1 after converter to/from string
      text :=: numberConverter.numberToString(currentConverter.value1);
   }

   int row2y := (int)(unit1Field.location.y + unit1Field.size.height + ypad);

   object unit2Label extends JLabel {
      text := currentConverter.unit2;
      labelFor = unit2Field;
      size := preferredSize;
      location := SwingUtil.point(unit1Label.location.x, row2y + baseline);
   }
   object unit2Field extends JTextField {
      location := SwingUtil.point(xpad + unit2Label.preferredSize.width + gap, row2y);
      size := SwingUtil.dimension(windowWidth - unit2Label.size.width - xpad - 2*gap, preferredSize.height);
      text :=: numberConverter.numberToString(currentConverter.value2);
   }

   object errorLabel extends JLabel {
      // The number converter provides an error when an invalid number is supplied
      text := numberConverter.error;
      location := SwingUtil.point(xpad, unit2Field.location.y + unit2Field.size.height + ypad + baseline);
      size := preferredSize;
   }

   // Converts the value strings to/from numbers.  numberToString to defines
   // a reverse method so it can be used in 2-way bindings.
   object numberConverter extends sc.util.NumberConverter {
   }
}
