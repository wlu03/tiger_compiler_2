.text
  li $v-local--t, 0
  li $v0, 5
  syscall
  move $v-local--n, $v0
  li $v-temp--tmp, 100
  bgt $v-local--n, $v-temp--tmp, return_main
  li $v-temp--tmp, 1
  sub $v-local--n, $v-local--n, $v-temp--tmp
  li $v-local--i, 0
loop0_main:
  bgt $v-local--i, $v-local--n, exit0_main
  li $v0, 5
  syscall
  move $v-local--t, $v0
  sll $v-temp--off, $v-local--i, 2
  addi $v-temp--base, $fp, 0
  sub $v-temp--addr, $v-temp--base, $v-temp--off
  sw $v-local--t, 0($v-temp--addr)
  addi $v-local--i, $v-local--i, 1
  j loop0_main
exit0_main:
  move $a0, $fp
  addi $a0, $a0, 0
  li $a1, 0
  move $a2, $v-local--n
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  addi $sp, $sp, 0
  li $v-local--i, 0
loop1_main:
  bgt $v-local--i, $v-local--n, exit1_main
  sll $v-temp--off, $v-local--i, 2
  addi $v-temp--base, $fp, 0
  sub $v-temp--addr, $v-temp--base, $v-temp--off
  lw $v-local--t, 0($v-temp--addr)
  move $a0, $v-local--t
  li $v0, 1
  syscall
  li $a0, 10
  li $v0, 11
  syscall
  addi $v-local--i, $v-local--i, 1
  j loop1_main
exit1_main:
return_main:
  li $v0, 10
  syscall
  move $v-param--A, $a0
  move $v-param--lo, $a1
  move $v-param--hi, $a2
  li $v-local--i, 0
  li $v-local--j, 0
  bge $v-param--lo, $v-param--hi, end_quicksort
  add $v-local--mid, $v-param--lo, $v-param--hi
  li $v-temp--tmp, 2
  div $v-local--mid, $v-local--mid, $v-temp--tmp
  sll $v-temp--off, $v-local--mid, 2
  sub $v-temp--addr, $v-param--A, $v-temp--off
  lw $v-local--pivot, 0($v-temp--addr)
  li $v-temp--tmp, 1
  sub $v-local--i, $v-param--lo, $v-temp--tmp
  addi $v-local--j, $v-param--hi, 1
loop0_quicksort:
loop1_quicksort:
  addi $v-local--i, $v-local--i, 1
  sll $v-temp--off, $v-local--i, 2
  sub $v-temp--addr, $v-param--A, $v-temp--off
  lw $v-local--x, 0($v-temp--addr)
  move $v-local--ti, $v-local--x
  blt $v-local--ti, $v-local--pivot, loop1_quicksort
loop2_quicksort:
  li $v-temp--tmp, 1
  sub $v-local--j, $v-local--j, $v-temp--tmp
  sll $v-temp--off, $v-local--j, 2
  sub $v-temp--addr, $v-param--A, $v-temp--off
  lw $v-local--x, 0($v-temp--addr)
  move $v-local--tj, $v-local--x
  bgt $v-local--tj, $v-local--pivot, loop2_quicksort
  bge $v-local--i, $v-local--j, exit0_quicksort
  sll $v-temp--off, $v-local--j, 2
  sub $v-temp--addr, $v-param--A, $v-temp--off
  sw $v-local--ti, 0($v-temp--addr)
  sll $v-temp--off, $v-local--i, 2
  sub $v-temp--addr, $v-param--A, $v-temp--off
  sw $v-local--tj, 0($v-temp--addr)
  j loop0_quicksort
exit0_quicksort:
  addi $v-local--j1, $v-local--j, 1
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  move $a0, $v-param--A
  move $a1, $v-param--lo
  move $a2, $v-local--j
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
  addi $v-local--j, $v-local--j, 1
  addi $sp, $sp, -12
  sw $a0, 0($sp)
  sw $a1, 4($sp)
  sw $a2, 8($sp)
  move $a0, $v-param--A
  move $a1, $v-local--j
  move $a2, $v-param--hi
  addi $sp, $sp, -8
  sw $fp, 0($sp)
  sw $ra, 4($sp)
  jal quicksort
  lw $fp, 0($sp)
  lw $ra, 4($sp)
  addi $sp, $sp, 8
  lw $a0, 0($sp)
  lw $a1, 4($sp)
  lw $a2, 8($sp)
  addi $sp, $sp, 12
end_quicksort:
  jr $ra

