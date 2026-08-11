package com.era.assistant

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ResearchNoteDialog(
    private val activity: AppCompatActivity
) {

    fun show(
        onSave: (String) -> Unit
    ) {

        val root =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dpToPx(22),
                    dpToPx(20),
                    dpToPx(22),
                    dpToPx(16)
                )

                background =
                    createRoundedBackground(
                        "#121722",
                        24
                    )
            }

        val title =
            TextView(activity).apply {

                text =
                    "Заметка"

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                textSize =
                    20f

                setPadding(
                    0,
                    0,
                    0,
                    dpToPx(16)
                )
            }

        root.addView(
            title
        )

        val noteInput =
            EditText(activity).apply {

                hint =
                    "Опиши здесь, что пошло не так..."

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                setHintTextColor(
                    Color.parseColor(
                        "#737B8A"
                    )
                )

                textSize =
                    16f

                gravity =
                    Gravity.TOP or
                        Gravity.START

                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                        android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

                minLines =
                    8

                maxLines =
                    14

                isVerticalScrollBarEnabled =
                    true

                setPadding(
                    dpToPx(16),
                    dpToPx(14),
                    dpToPx(16),
                    dpToPx(14)
                )

                background =
                    createRoundedBackground(
                        "#1A1F2A",
                        16
                    )
            }

        root.addView(
            noteInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(240)
            )
        )

        val buttons =
            LinearLayout(activity).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.END or
                        Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dpToPx(14),
                    0,
                    0
                )
            }

        val cancelButton =
            TextView(activity).apply {

                text =
                    "Отмена"

                setTextColor(
                    Color.parseColor(
                        "#9AA1AE"
                    )
                )

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setPadding(
                    dpToPx(16),
                    dpToPx(10),
                    dpToPx(16),
                    dpToPx(10)
                )
            }

        val saveButton =
            TextView(activity).apply {

                text =
                    "Сохранить"

                setTextColor(
                    Color.parseColor(
                        "#F1F1F4"
                    )
                )

                textSize =
                    15f

                gravity =
                    Gravity.CENTER

                setPadding(
                    dpToPx(18),
                    dpToPx(10),
                    dpToPx(18),
                    dpToPx(10)
                )

                background =
                    createRoundedBackground(
                        "#242B38",
                        14
                    )
            }

        buttons.addView(
            cancelButton
        )

        buttons.addView(
            saveButton
        )

        root.addView(
            buttons
        )

        val dialog =
            AlertDialog.Builder(activity)
                .setView(root)
                .create()

        cancelButton.setOnClickListener {

            dialog.dismiss()
        }

        saveButton.setOnClickListener {

            val noteText =
                noteInput
                    .text
                    .toString()
                    .trim()

            if (
                noteText.isNotBlank()
            ) {

                onSave(
                    noteText
                )

                dialog.dismiss()
            }
        }

        dialog.setOnShowListener {

            dialog.window
                ?.setBackgroundDrawableResource(
                    android.R.color.transparent
                )
        }

        dialog.show()

        noteInput.requestFocus()
    }

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
                activity
                    .resources
                    .displayMetrics
                    .density
            ).toInt()
    }

    private fun createRoundedBackground(
        color: String,
        radiusDp: Int
    ): GradientDrawable {

        val drawable =
            GradientDrawable()

        drawable.shape =
            GradientDrawable.RECTANGLE

        drawable.setColor(
            Color.parseColor(
                color
            )
        )

        drawable.cornerRadius =
            dpToPx(
                radiusDp
            ).toFloat()

        return drawable
    }
}